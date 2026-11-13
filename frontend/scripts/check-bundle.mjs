// Budżet rozmiaru i test manifestu (obszary w osobnych chunkach). Uruchamiane po `vite build`.
// Wymusza mechanicznie, że pacjent nie pobiera kodu paneli administracyjnych.
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

export const AREAS = ['pacjent', 'pracownik', 'administracja', 'platforma'];
/** Progi informacyjne (po pierwszym pomiarze z zapasem): gzip, w bajtach. */
export const BUDGET = { initial: 200 * 1024, area: 150 * 1024 };

const areaOf = (key) => AREAS.find((a) => key.startsWith(`src/app/areas/${a}/`));

/** Domknięcie statycznych importów (bez importów dynamicznych) od zadanego klucza manifestu. */
function staticClosure(manifest, startKey) {
  const seen = new Set();
  const stack = [startKey];
  while (stack.length > 0) {
    const key = stack.pop();
    if (seen.has(key)) continue;
    seen.add(key);
    for (const next of manifest[key]?.imports ?? []) stack.push(next);
  }
  return seen;
}

/**
 * @param {Record<string, {file: string, isEntry?: boolean, imports?: string[]}>} manifest
 * @param {(file: string) => number} gzipSize rozmiar pliku po kompresji gzip
 * @returns {string[]} lista naruszeń (pusta = OK)
 */
export function evaluate(manifest, gzipSize) {
  const violations = [];
  const entryKey = Object.keys(manifest).find((k) => manifest[k]?.isEntry);
  if (!entryKey) return ['Brak wejścia (isEntry) w manifeście'];

  const areaKeys = Object.keys(manifest).filter((k) => areaOf(k) !== undefined);
  const missing = AREAS.filter((a) => !areaKeys.some((k) => areaOf(k) === a));
  for (const a of missing) violations.push(`Brak osobnego chunku obszaru: ${a}`);

  const entryClosure = staticClosure(manifest, entryKey);
  for (const key of entryClosure) {
    const area = areaOf(key);
    if (area) violations.push(`Wejście importuje statycznie obszar "${area}" (${key})`);
  }

  for (const key of areaKeys) {
    const own = areaOf(key);
    for (const dep of staticClosure(manifest, key)) {
      const other = areaOf(dep);
      if (other && other !== own) {
        violations.push(`Obszar "${own}" importuje statycznie obszar "${other}"`);
      }
    }
  }

  let initial = 0;
  for (const key of entryClosure) {
    const entry = manifest[key];
    if (entry) initial += gzipSize(entry.file);
  }
  if (initial > BUDGET.initial) {
    violations.push(
      `Chunk początkowy ${kb(initial)} kB (gzip) przekracza ${kb(BUDGET.initial)} kB`,
    );
  }
  for (const key of areaKeys) {
    const entry = manifest[key];
    const size = entry ? gzipSize(entry.file) : 0;
    if (size > BUDGET.area) {
      violations.push(
        `Chunk obszaru "${areaOf(key)}" ${kb(size)} kB (gzip) przekracza ${kb(BUDGET.area)} kB`,
      );
    }
  }
  return violations;
}

const kb = (bytes) => (bytes / 1024).toFixed(1);

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const dist = resolve(dirname(fileURLToPath(import.meta.url)), '../dist');
  const manifestPath = resolve(dist, '.vite/manifest.json');
  if (!existsSync(manifestPath)) {
    console.error(
      '[check:bundle] Brak dist/.vite/manifest.json. Najpierw uruchom "npm run build".',
    );
    process.exit(1);
  }
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  const sizeOf = (file) => gzipSync(readFileSync(resolve(dist, file))).length;
  const violations = evaluate(manifest, sizeOf);
  if (violations.length > 0) {
    console.error('[check:bundle] Naruszenia:\n - ' + violations.join('\n - '));
    process.exit(1);
  }
  console.log('[check:bundle] OK');
}
