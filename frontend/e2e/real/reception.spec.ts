import { expect, test, type Page } from '@playwright/test';
import { account } from '../fixtures/accounts';
import { login } from './helpers';

const receptionist = account('klinika-a', 'rejestracja');
const stamp = Date.now().toString(36);

/**
 * Wizyty w tych testach zakłada rejestrator dla nowego pacjenta z unikalnym nazwiskiem: testy nie czyszczą
 * danych, a kolejne uruchomienia nie mogą na siebie wpływać.
 */
async function bookNewPatient(page: Page, surname: string) {
  await login(page, receptionist);
  await expect(page).toHaveURL(/\/pracownik\/terminarz/);
  await expect(page.getByRole('grid')).toBeVisible();

  // Pierwszy dzień (od jutra), w którym jest dość wolnych terminów
  const free = page.getByRole('gridcell', { name: /wolny$/ });
  for (let day = 0; day < 8; day++) {
    await page.getByRole('button', { name: 'Następny dzień' }).click();
    await expect(page.getByRole('grid')).toBeVisible();
    if ((await free.count()) > 8) break;
  }
  const cell = free.nth(4);
  const cellName = (await cell.getAttribute('aria-label')) ?? '';
  const doctor = cellName.split(',')[0] ?? '';
  const time = cellName.split(',')[1]?.trim() ?? '';
  await cell.click();

  const dialog = page.getByRole('dialog', { name: 'Umów wizytę dla pacjenta' });
  await dialog.getByLabel('Usługa').selectOption({ index: 1 });
  await dialog.getByRole('button', { name: 'Nowy pacjent' }).click();
  await dialog.getByLabel('Imię').fill(`Test${stamp}`);
  await dialog.getByLabel('Nazwisko').fill(surname);
  await dialog.getByLabel('Data urodzenia').fill('1985-03-04');
  await dialog.getByRole('button', { name: 'Zapisz pacjenta' }).click();
  await expect(dialog.getByText(`Test${stamp} ${surname}`)).toBeVisible();
  await dialog.getByRole('button', { name: 'Umów wizytę' }).click();
  await expect(page.getByText('Wizyta została umówiona')).toBeVisible();

  const booked = page.getByRole('gridcell', { name: new RegExp(`^${doctor}, ${time}, zajęty`) });
  await expect(booked).toBeVisible();
  return { doctor, time };
}

/** Blok wizyty przykrywa komórkę siatki, więc klikamy blok. */
const appointmentBlock = (page: Page, surname: string) =>
  page.locator('[data-appt]').filter({ hasText: new RegExp(surname) });

test('rejestrator zakłada pacjenta, umawia i odwołuje wizytę, a termin wraca do puli', async ({
  page,
}) => {
  const surname = `Odw${stamp}`;
  const { doctor, time } = await bookNewPatient(page, surname);

  await appointmentBlock(page, surname).first().click();
  await page
    .getByRole('region', { name: 'Szczegóły wizyty' })
    .getByRole('button', { name: 'Odwołaj' })
    .click();
  await page.getByRole('alertdialog').getByRole('button', { name: 'Odwołaj wizytę' }).click();
  await expect(page.getByText('Wizyta została odwołana')).toBeVisible();
  await expect(appointmentBlock(page, surname)).toHaveCount(0);
  await expect(
    page.getByRole('gridcell', { name: new RegExp(`^${doctor}, ${time}, wolny`) }),
  ).toBeVisible();
});

test('rejestrator przesuwa wizytę na inny termin', async ({ page }) => {
  const surname = `Prz${stamp}`;
  await bookNewPatient(page, surname);

  await appointmentBlock(page, surname).first().click();
  await page
    .getByRole('region', { name: 'Szczegóły wizyty' })
    .getByRole('button', { name: 'Przesuń' })
    .click();
  const reschedule = page.getByRole('dialog', { name: 'Przesuń wizytę' });
  // Wolne terminy bywają dopiero w którymś z kolejnych tygodni
  const slots = reschedule.locator('button[data-slot]');
  for (let week = 0; week < 4; week++) {
    await slots
      .first()
      .or(reschedule.getByText('Brak wolnych terminów w tym tygodniu, sprawdź kolejny'))
      .waitFor();
    if ((await slots.count()) > 0) break;
    await reschedule.getByRole('button', { name: 'Następny tydzień' }).click();
  }
  await slots.last().click();
  await reschedule.getByRole('button', { name: 'Przesuń wizytę' }).click();
  await expect(page.getByText('Wizyta została przesunięta')).toBeVisible();
});

test('rejestrator wyszukuje pacjenta po nazwisku bez danych w adresie', async ({ page }) => {
  await login(page, receptionist);
  await page.goto('/pracownik/pacjenci');
  await page.getByLabel('Szukaj pacjenta').fill('Kowalski');
  await expect(
    page
      .getByRole('list', { name: 'Wyniki wyszukiwania' })
      .or(page.getByText('Nie znaleziono pacjentów')),
  ).toBeVisible();
  expect(page.url()).not.toContain('Kowalski');
});
