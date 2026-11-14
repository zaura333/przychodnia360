import { defineConfig, devices } from '@playwright/test';

// BASE_URL ustawione = test przeciwko działającej aplikacji (np. kontener web, żywe demo): bez webServer.
const baseURL = process.env.BASE_URL ?? 'http://localhost:4173';

export default defineConfig({
  testDir: './e2e',
  // Testy na prawdziwym backendzie (e2e/real) zmieniają dane i idą po kolei; uruchamia je `npm run test:e2e:real`
  testIgnore: process.env.E2E_REAL ? [] : ['**/real/**'],
  testMatch: process.env.E2E_REAL ? ['**/real/**/*.spec.ts'] : ['**/*.spec.ts'],
  fullyParallel: !process.env.E2E_REAL,
  ...(process.env.E2E_REAL ? { workers: 1, timeout: 60_000 } : {}),
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL,
    // Potwierdza, że aplikacja pokazuje czas warszawski niezależnie od strefy urządzenia
    timezoneId: 'America/New_York',
    locale: 'pl-PL',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  // Chromium jedyny w demo (Firefox i WebKit poza zakresem)
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  ...(process.env.BASE_URL
    ? {}
    : {
        webServer: {
          command: 'npm run build && npm run preview -- --port 4173 --strictPort',
          env: { VITE_DEV_BACKEND: process.env.VITE_DEV_BACKEND ?? 'http://127.0.0.1:8080' },
          url: baseURL,
          reuseExistingServer: !process.env.CI,
          timeout: 180_000,
        },
      }),
});
