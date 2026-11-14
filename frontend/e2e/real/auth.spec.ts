import { expect, test } from '@playwright/test';
import { account, MAILPIT, SEED_PASSWORD } from '../fixtures/accounts';
import { expectNoPii, login } from './helpers';

const unique = `e2e-${Date.now().toString(36)}`;

test('rejestracja pacjenta, logowanie i reset hasła z linkiem z Mailpit', async ({
  page,
  request,
}) => {
  const email = `${unique}@klinika-a.example.test`;
  const firstPassword = 'pierwsze-haslo-e2e-1';
  const secondPassword = 'drugie-haslo-e2e-2';

  await page.goto('/rejestracja');
  await page.getByLabel('Placówka').selectOption('klinika-a');
  await page.getByLabel('Imię').fill('E2E');
  await page.getByLabel('Nazwisko').fill('Rejestracja');
  await page.getByLabel('Data urodzenia').fill('1992-07-08');
  await page.getByLabel('Adres e-mail').fill(email);
  await page.getByLabel('Hasło', { exact: true }).fill(firstPassword);
  await page.getByLabel('Powtórz hasło').fill(firstPassword);
  await page.getByRole('button', { name: 'Załóż konto' }).click();
  await expect(page.getByText(/Jeśli adres jest wolny, konto zostało utworzone/)).toBeVisible();

  await login(page, { tenant: 'klinika-a', email, password: firstPassword });
  await expect(page).toHaveURL(/\/pacjent\/wizyty/);
  await page.getByRole('button', { name: 'Wyloguj' }).click();
  await expect(page).toHaveURL(/\/logowanie/);

  await page.goto('/nie-pamietam-hasla');
  await page.getByLabel('Placówka').selectOption('klinika-a');
  await page.getByLabel('Adres e-mail').fill(email);
  await page.getByRole('button', { name: 'Wyślij link' }).click();
  await expect(
    page.getByText('Jeśli konto istnieje, wysłaliśmy wiadomość z linkiem.'),
  ).toBeVisible();

  // Link z wiadomości w Mailpit
  let link = '';
  await expect
    .poll(
      async () => {
        const list = (await (
          await request.get(`${MAILPIT}/api/v1/search?query=to:${email}`)
        ).json()) as { messages?: { ID: string }[] };
        const id = list.messages?.[0]?.ID;
        if (!id) return '';
        const message = (await (await request.get(`${MAILPIT}/api/v1/message/${id}`)).json()) as {
          Text: string;
        };
        link = /https?:\/\/\S*reset-hasla\?token=[^\s"<]+/.exec(message.Text)?.[0] ?? '';
        return link;
      },
      { timeout: 20_000 },
    )
    .not.toBe('');
  const token = new URL(link).searchParams.get('token') ?? '';
  await page.goto(`/reset-hasla?token=${token}`);
  await page.getByLabel('Nowe hasło').fill(secondPassword);
  await page.getByLabel('Powtórz hasło').fill(secondPassword);
  // Token znika z paska adresu
  await expect.poll(() => new URL(page.url()).search).toBe('');
  await page.getByRole('button', { name: 'Ustaw hasło' }).click();
  await expect(page).toHaveURL(/\/logowanie/);

  await login(page, { tenant: 'klinika-a', email, password: secondPassword });
  await expect(page).toHaveURL(/\/pacjent\/wizyty/);
  await expectNoPii(page, [unique, 'example.test']);
});

test('dane logowania z jednej placówki nie działają w drugiej', async ({ page }) => {
  const who = account('klinika-a', 'pacjent1');
  await page.goto('/logowanie');
  await page.getByLabel('Placówka').selectOption('klinika-b');
  await page.getByLabel('Adres e-mail').fill(who.email);
  await page.getByLabel('Hasło', { exact: true }).fill(SEED_PASSWORD);
  await page.getByRole('button', { name: 'Zaloguj się' }).click();
  await expect(page.getByText('Nieprawidłowe dane logowania')).toBeVisible();
  await expect(page).toHaveURL(/\/logowanie/);
});

test('po wygaśnięciu sesji użytkownik wraca na logowanie, a po zalogowaniu na właściwą trasę', async ({
  page,
  context,
}) => {
  const who = account('klinika-a', 'pacjent3');
  await login(page, who);
  await page.goto('/pacjent/moje-dane');
  await expect(page.getByRole('heading', { name: 'Moje dane' })).toBeVisible();
  await context.clearCookies();
  await page.reload();
  await expect(page).toHaveURL(/\/logowanie/);
  await page.getByLabel('Placówka').selectOption(who.tenant);
  await page.getByLabel('Adres e-mail').fill(who.email);
  await page.getByLabel('Hasło', { exact: true }).fill(who.password);
  await page.getByRole('button', { name: 'Zaloguj się' }).click();
  await expect(page).toHaveURL(/\/pacjent\/moje-dane/);
});

test('po wylogowaniu i zalogowaniu kogoś innego nie ma śladów poprzednich danych', async ({
  page,
}) => {
  await login(page, account('klinika-a', 'pacjent1'));
  await page.goto('/pacjent/moje-dane');
  await expect(page.getByRole('heading', { name: 'Moje dane' })).toBeVisible();
  const firstName = await page.locator('dd').first().textContent();
  await page.getByRole('button', { name: 'Wyloguj' }).click();
  await login(page, account('klinika-a', 'pacjent2'));
  await page.goto('/pacjent/moje-dane');
  await expect(page.getByRole('heading', { name: 'Moje dane' })).toBeVisible();
  await expect(page.locator('dd').first()).not.toHaveText(firstName ?? '');
});

test('pacjent nie wchodzi do obszarów pracowniczych (403), a obcy zasób to 404', async ({
  page,
}) => {
  await login(page, account('klinika-a', 'pacjent1'));
  await page.goto('/administracja/uslugi');
  await expect(page.getByRole('heading', { name: 'Brak dostępu' })).toBeVisible();
  await page.goto('/pacjent/uslugi/00000000-0000-0000-0000-000000000000');
  await expect(page.getByRole('heading', { name: 'Nie znaleziono strony' })).toBeVisible();
});
