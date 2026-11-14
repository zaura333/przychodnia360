import { expect, type Page } from '@playwright/test';

export interface Credentials {
  tenant: string;
  email: string;
  password: string;
}

/** Logowanie przez formularz; czeka na przekierowanie z ekranu logowania. */
export async function login(page: Page, who: Credentials, path = '/logowanie') {
  await page.goto(path);
  await page.getByLabel('Placówka').selectOption(who.tenant);
  await page.getByLabel('Adres e-mail').fill(who.email);
  await page.getByLabel('Hasło', { exact: true }).fill(who.password);
  await page.getByRole('button', { name: 'Zaloguj się' }).click();
  await expect(page).not.toHaveURL(/\/logowanie/);
}

/** Zbiera naruszenia CSP i błędy konsoli, żeby test mógł je sprawdzić na końcu. */
export async function trackBrowserProblems(page: Page) {
  const consoleErrors: string[] = [];
  page.on('console', (m) => {
    // Odpowiedzi 4xx/5xx z API są oczekiwane w testach negatywnych (komunikat "Failed to load resource")
    if (m.type() === 'error' && !m.text().includes('Failed to load resource')) {
      consoleErrors.push(m.text());
    }
  });
  await page.addInitScript(() => {
    const w = window as unknown as { __csp: string[] };
    w.__csp = [];
    document.addEventListener('securitypolicyviolation', (e) => {
      w.__csp.push(`${e.violatedDirective} ${e.blockedURI}`);
    });
  });
  return {
    consoleErrors,
    violations: () => page.evaluate(() => (window as unknown as { __csp: string[] }).__csp),
  };
}

/** Adres i magazyny przeglądarki nie mogą zawierać danych osobowych (tylko identyfikatory i kod placówki). */
export async function expectNoPii(page: Page, forbidden: readonly string[]) {
  const url = decodeURIComponent(page.url());
  for (const text of forbidden) expect(url.toLowerCase()).not.toContain(text.toLowerCase());
  const storage = await page.evaluate(() => ({
    local: Object.keys(window.localStorage),
    session: Object.keys(window.sessionStorage),
    localValues: Object.values(window.localStorage).join(' '),
  }));
  expect(storage.local.every((k) => k === 'p360.tenantSlug')).toBe(true);
  expect(storage.session).toEqual([]);
  for (const text of forbidden)
    expect(storage.localValues.toLowerCase()).not.toContain(text.toLowerCase());
}

/**
 * Przechodzi kreator do kroku potwierdzenia i zwraca etykietę wybranego terminu. Szuka pierwszego
 * wolnego terminu w kolejnych tygodniach (do 6), `skip` pomija tyle pierwszych terminów.
 */
export async function pickFirstSlot(page: Page, skip = 0): Promise<string> {
  await page.getByRole('radio').first().check();
  await page.getByRole('button', { name: 'Dalej' }).click();
  await page.getByRole('radio').first().check();
  await page.getByRole('button', { name: 'Dalej' }).click();
  await expect(page.getByText(/Krok 3 z 4/)).toBeVisible();
  for (let week = 0; week < 6; week++) {
    const slots = page.locator('button[data-slot]');
    // Czekamy, aż tydzień się załaduje: są terminy albo komunikat o ich braku
    await slots
      .first()
      .or(page.getByText('Brak wolnych terminów w tym tygodniu, sprawdź kolejny'))
      .waitFor();
    if ((await slots.count()) > skip) {
      const label = (await slots.nth(skip).getAttribute('aria-label')) ?? '';
      await slots.nth(skip).click();
      return label;
    }
    await page.getByRole('button', { name: 'Następny tydzień' }).click();
  }
  throw new Error('Brak wolnych terminów w kolejnych tygodniach (seed?)');
}
