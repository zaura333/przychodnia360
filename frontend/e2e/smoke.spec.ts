import { expect, test, type Page } from '@playwright/test';

/** Zbiera naruszenia CSP (zdarzenia securitypolicyviolation) i błędy konsoli. */
async function trackCsp(page: Page) {
  const consoleErrors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error') consoleErrors.push(m.text());
  });
  await page.addInitScript(() => {
    (window as unknown as { __csp: string[] }).__csp = [];
    document.addEventListener('securitypolicyviolation', (e) => {
      (window as unknown as { __csp: string[] }).__csp.push(
        `${e.violatedDirective} ${e.blockedURI}`,
      );
    });
  });
  return {
    violations: () => page.evaluate(() => (window as unknown as { __csp: string[] }).__csp),
    consoleErrors,
  };
}

const refresh401 = (page: Page) =>
  page.route('**/api/v1/auth/refresh', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({ type: '/problems/unauthorized', title: 'x', status: 401 }),
    }),
  );

test('strona się ładuje: tytuł, język i baner demo', async ({ page }) => {
  await refresh401(page);
  await page.goto('/logowanie');
  await expect(page).toHaveTitle('Przychodnia360 (wersja demo)');
  await expect(page.locator('html')).toHaveAttribute('lang', 'pl');
  await expect(
    page.getByRole('region', { name: 'Informacja o wersji demonstracyjnej' }),
  ).toBeVisible();
});

test('niezalogowany na /pracownik trafia na /logowanie', async ({ page }) => {
  await refresh401(page);
  await page.goto('/pracownik');
  await expect(page).toHaveURL(/\/logowanie$/);
  await expect(page.getByRole('heading', { name: 'Logowanie' })).toBeVisible();
});

test('nieznana ścieżka pokazuje NotFoundPage (fallback SPA działa w podglądzie)', async ({
  page,
}) => {
  await refresh401(page);
  await page.goto('/ta-sciezka-nie-istnieje');
  await expect(page.getByRole('heading', { name: 'Nie znaleziono strony' })).toBeVisible();
});

test('brak naruszeń CSP i błędów konsoli na trasach publicznych', async ({ page }) => {
  await refresh401(page);
  const csp = await trackCsp(page);
  for (const path of ['/logowanie', '/pracownik', '/ta-sciezka-nie-istnieje']) {
    await page.goto(path);
    await expect(
      page.getByRole('region', { name: 'Informacja o wersji demonstracyjnej' }),
    ).toBeVisible();
  }
  expect(await csp.violations()).toEqual([]);
  // 401 z odświeżania to oczekiwany stan anonimowy, ale nie błąd skryptu
  expect(csp.consoleErrors.filter((e) => /Content Security Policy/i.test(e))).toEqual([]);
});

test('test CSP wychwytuje wstrzyknięty skrypt inline', async ({ page }) => {
  await refresh401(page);
  const csp = await trackCsp(page);
  await page.goto('/logowanie');
  await page.evaluate(() => {
    const s = document.createElement('script');
    s.textContent = 'window.__injected = true';
    document.body.appendChild(s);
  });
  const violations = await csp.violations();
  expect(violations.some((v) => v.startsWith('script-src'))).toBe(true);
  expect(
    await page.evaluate(() => (window as unknown as { __injected?: boolean }).__injected),
  ).toBeUndefined();
});

test('odpowiedzi mają nagłówki bezpieczeństwa i polityki cache', async ({ page, request }) => {
  await refresh401(page);
  const index = await request.get('/');
  expect(index.headers()['content-security-policy']).toContain("default-src 'self'");
  expect(index.headers()['x-content-type-options']).toBe('nosniff');
  expect(index.headers()['cache-control']).toBe('no-store');
  await page.goto('/logowanie');
  const asset = await page.evaluate(
    () =>
      [...document.querySelectorAll('script[src*="/assets/"]')].map(
        (s) => (s as HTMLScriptElement).src,
      )[0],
  );
  expect(asset).toBeTruthy();
  const assetResponse = await request.get(asset ?? '');
  expect(assetResponse.headers()['cache-control']).toContain('immutable');
});

test('gdy odświeżanie sesji zawodzi błędem sieci, logowanie pokazuje komunikat o niedostępności serwera', async ({
  page,
}) => {
  await page.route('**/api/v1/auth/refresh', (route) => route.abort('failed'));
  await page.goto('/logowanie');
  await expect(page.getByRole('alert')).toContainText('Nie można połączyć z serwerem');
});
