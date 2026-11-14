// Build produkcyjny ze stronami pomiarowymi (VITE_BENCH=true) do osobnego katalogu dist-bench.
// Serwowany przez `vite preview` z prawdziwymi nagłówkami CSP, więc pomiary są zgodne z produkcją.
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const result = spawnSync('npx', ['vite', 'build', '--outDir', 'dist-bench', '--emptyOutDir'], {
  cwd: root,
  stdio: 'inherit',
  shell: process.platform === 'win32',
  env: { ...process.env, VITE_BENCH: 'true' },
});
process.exit(result.status ?? 1);
