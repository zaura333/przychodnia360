// Generuje klienta API (Orval) z ../api/openapi.json. Wygenerowany kod nie jest commitowany.
// Z flagą --soft (postinstall) brak kontraktu nie przerywa instalacji.
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const contract = resolve(root, '../api/openapi.json');
const soft = process.argv.includes('--soft');

if (!existsSync(contract)) {
  const message =
    'Brak api/openapi.json. Wygeneruj kontrakt backendu: w katalogu backend uruchom "./mvnw verify" ' +
    '(plik powstaje w api/openapi.json), a potem "npm run api:generate".';
  if (soft) {
    console.warn(`[api:generate] ${message}`);
    process.exit(0);
  }
  console.error(`[api:generate] ${message}`);
  process.exit(1);
}

if (!existsSync(resolve(root, 'orval.config.ts'))) {
  console.warn('[api:generate] Brak orval.config.ts, pomijam generowanie.');
  process.exit(0);
}

const result = spawnSync('npx', ['orval', '--config', 'orval.config.ts'], {
  cwd: root,
  stdio: 'inherit',
  shell: process.platform === 'win32',
});
process.exit(result.status ?? 1);
