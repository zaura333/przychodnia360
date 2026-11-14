// Uruchamia testy e2e na prawdziwym backendzie (e2e/real). Wymaga działającego docker compose z backendem
// (profil core, seed `local`, krawędź na porcie 8090: VITE_DEV_BACKEND) oraz Mailpit. Dodatkowe argumenty trafiają do Playwright.
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const result = spawnSync('npx', ['playwright', 'test', ...process.argv.slice(2)], {
  cwd: root,
  stdio: 'inherit',
  shell: true,
  env: { ...process.env, E2E_REAL: '1' },
});
process.exit(result.status ?? 1);
