/** Formatowanie wartości prezentacyjnych (bez czasu: ten jest w shared/lib/datetime). */

const pln = new Intl.NumberFormat('pl-PL', { style: 'currency', currency: 'PLN' });

/** Kwota z groszy, np. 1250 -> "12,50 zł". */
export function formatPln(grosze: number): string {
  return pln.format(grosze / 100);
}

export function formatDuration(minutes: number): string {
  const total = Math.max(0, Math.round(minutes));
  const h = Math.floor(total / 60);
  const m = total % 60;
  if (h === 0) return `${m} min`;
  return m === 0 ? `${h} godz.` : `${h} godz. ${m} min`;
}

/** Polska odmiana liczebnika: 1 -> one, 2-4 (poza 12-14) -> few, reszta -> many. */
export function formatPolishPlural(n: number, one: string, few: string, many: string): string {
  const abs = Math.abs(n);
  if (abs === 1) return one;
  const last = abs % 10;
  const lastTwo = abs % 100;
  if (last >= 2 && last <= 4 && !(lastTwo >= 12 && lastTwo <= 14)) return few;
  return many;
}

/** Numer pacjenta, np. 123 -> "P-000123". */
export function formatPatientNumber(n: number): string {
  return `P-${String(Math.trunc(n)).padStart(6, '0')}`;
}
