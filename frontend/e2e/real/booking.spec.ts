import { expect, test, type Browser, type Page } from '@playwright/test';
import { account } from '../fixtures/accounts';
import { expectNoPii, login, pickFirstSlot, trackBrowserProblems } from './helpers';

const patient1 = account('klinika-a', 'pacjent1');
const patient2 = account('klinika-a', 'pacjent2');

test.describe.configure({ mode: 'serial' });

async function newPage(browser: Browser): Promise<Page> {
  const context = await browser.newContext({ locale: 'pl-PL', timezoneId: 'America/New_York' });
  return context.newPage();
}

test('pacjent rezerwuje wizytę i widzi ją na liście, bez PII w adresie i magazynach', async ({
  browser,
}) => {
  const page = await newPage(browser);
  const problems = await trackBrowserProblems(page);
  await login(page, patient1);
  await expect(page).toHaveURL(/\/pacjent\/wizyty/);

  await page.getByRole('link', { name: 'Umów wizytę' }).first().click();
  await expect(page.getByText(/Krok 1 z 4/)).toBeVisible();
  await pickFirstSlot(page, 2);
  await page.getByRole('button', { name: 'Dalej' }).click();
  await expect(page.getByText(/Krok 4 z 4/)).toBeVisible();
  await expectNoPii(page, ['pacjent1', 'example.test']);
  await page.getByRole('button', { name: 'Potwierdzam rezerwację' }).click();
  await expect(page.getByRole('heading', { name: 'Wizyta zarezerwowana' })).toBeVisible();

  await page.getByRole('main').getByRole('link', { name: 'Moje wizyty' }).click();
  await expect(page.getByText('Zaplanowana').first()).toBeVisible();
  await expect(page.getByRole('button', { name: /Anuluj|Odwołaj/ })).toHaveCount(0);
  await expect(page.getByText('Aby odwołać wizytę, skontaktuj się z rejestracją.')).toBeVisible();
  await expectNoPii(page, ['pacjent1', 'example.test']);
  expect(await problems.violations()).toEqual([]);
  expect(problems.consoleErrors).toEqual([]);
  await page.context().close();
});

test('wyścig: dwóch pacjentów rezerwuje ten sam termin, jeden dostaje komunikat i odświeżone terminy', async ({
  browser,
}) => {
  const a = await newPage(browser);
  const b = await newPage(browser);
  await login(a, patient1);
  await login(b, patient2);
  for (const page of [a, b]) await page.goto('/pacjent/rezerwacja');

  // Obaj dochodzą do potwierdzenia na tym samym, czwartym wolnym terminie
  const slotA = await pickFirstSlot(a, 3);
  const slotB = await pickFirstSlot(b, 3);
  expect(slotA).toBe(slotB);
  for (const page of [a, b]) await page.getByRole('button', { name: 'Dalej' }).click();

  await a.getByRole('button', { name: 'Potwierdzam rezerwację' }).click();
  await expect(a.getByRole('heading', { name: 'Wizyta zarezerwowana' })).toBeVisible();

  await b.getByRole('button', { name: 'Potwierdzam rezerwację' }).click();
  await expect(
    b.getByText('Ten termin został właśnie zajęty. Wybierz inną godzinę.'),
  ).toBeVisible();
  await expect(b.getByText(/Krok 3 z 4/)).toBeVisible();
  // Zajęty termin znika z odświeżonej listy, a wybór jest wyczyszczony
  await expect(b.getByRole('button', { name: slotB })).toHaveCount(0);
  await expect(b.getByRole('button', { name: 'Dalej' })).toBeDisabled();
  await a.context().close();
  await b.context().close();
});
