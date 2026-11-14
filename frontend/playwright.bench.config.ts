import { defineConfig, devices } from '@playwright/test';

// Pomiary prototypu terminarza (poza zwykłym zestawem e2e): `npm run bench:schedule`
export default defineConfig({
  testDir: './bench',
  workers: 1,
  timeout: 180_000,
  reporter: 'list',
  use: {
    baseURL: 'http://localhost:4174',
    timezoneId: 'America/New_York',
    locale: 'pl-PL',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command:
      'node scripts/bench-build.mjs && npm run preview -- --outDir dist-bench --port 4174 --strictPort',
    url: 'http://localhost:4174',
    reuseExistingServer: false,
    timeout: 180_000,
  },
});
