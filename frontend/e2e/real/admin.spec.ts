import { expect, test } from '@playwright/test';
import { account } from '../fixtures/accounts';
import { login } from './helpers';

const admin = account('klinika-a', 'admin');
const stamp = Date.now().toString(36);

test('administrator dodaje usługę, a pacjent widzi ją w kreatorze', async ({ page, browser }) => {
  const name = `Usluga e2e ${stamp}`;
  await login(page, admin);
  await expect(page).toHaveURL(/\/administracja\/uslugi/);
  await page.getByRole('button', { name: 'Dodaj usługę' }).click();
  const dialog = page.getByRole('dialog', { name: 'Nowa usługa' });
  await dialog.getByLabel('Nazwa').fill(name);
  await dialog.getByLabel('Kategoria').selectOption({ index: 1 });
  await dialog.getByLabel('Cena').fill('123,45');
  await dialog.getByLabel('Czas trwania (minuty)').fill('20');
  await dialog.getByRole('button', { name: 'Zapisz' }).click();
  await expect(page.getByText('Dodano usługę')).toBeVisible();
  await expect(page.getByRole('row', { name: new RegExp(name) })).toContainText('123,45');

  const patientContext = await browser.newContext({ locale: 'pl-PL' });
  const patient = await patientContext.newPage();
  await login(patient, account('klinika-a', 'pacjent1'));
  await patient.goto('/pacjent/rezerwacja');
  await expect(patient.getByRole('radio', { name: new RegExp(name) })).toBeVisible();
  await patientContext.close();

  // Sprzątanie: dezaktywacja usługi testowej
  await page.getByRole('button', { name: `Dezaktywuj usługę ${name}` }).click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Dezaktywuj usługę' }).click();
  await expect(page.getByText('Usługa została dezaktywowana')).toBeVisible();
});

test('administrator widzi pracowników z grafikiem i przegląd wizyt tylko do odczytu', async ({
  page,
}) => {
  await login(page, admin);
  await page.goto('/administracja/pracownicy');
  await expect(page.getByRole('table', { name: 'Pracownicy placówki' })).toBeVisible();
  await page
    .getByRole('link', { name: /^Grafik pracownika/ })
    .first()
    .click();
  await expect(page.getByRole('heading', { name: 'Grafik tygodniowy' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Urlopy i dodatkowe zmiany' })).toBeVisible();
  await page.goto('/administracja/wizyty');
  await expect(page.getByRole('heading', { name: 'Wizyty' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Przesuń|Odwołaj/ })).toHaveCount(0);
});

test('administrator platformy dodaje placówkę', async ({ page }) => {
  const email = process.env.PLATFORM_ADMIN_EMAIL;
  const password = process.env.PLATFORM_ADMIN_PASSWORD;
  test.skip(
    !email || !password,
    'Brak PLATFORM_ADMIN_EMAIL i PLATFORM_ADMIN_PASSWORD (konto z profilu bootstrap-admin)',
  );
  await page.goto('/platforma/logowanie');
  await page.getByLabel('Adres e-mail').fill(email ?? '');
  await page.getByLabel('Hasło', { exact: true }).fill(password ?? '');
  await page.getByRole('button', { name: 'Zaloguj się' }).click();
  await expect(page).toHaveURL(/\/platforma\/placowki/);
  const slug = `e2e-${stamp}`.slice(0, 40);
  await page.getByRole('button', { name: 'Dodaj placówkę' }).click();
  const dialog = page.getByRole('dialog', { name: 'Nowa placówka' });
  await dialog.getByLabel('Nazwa placówki').fill(`Placówka ${stamp}`);
  await dialog.getByLabel('Kod placówki').fill(slug);
  await dialog.getByRole('button', { name: 'Dodaj placówkę' }).click();
  await expect(page.getByRole('cell', { name: slug })).toBeVisible();
});
