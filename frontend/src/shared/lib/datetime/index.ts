import { TZDate, tz } from '@date-fns/tz';
import { format, formatDistance } from 'date-fns';
import { pl } from 'date-fns/locale/pl';

/**
 * Jedyne miejsce operacji na czasie w aplikacji (reguła lintera blokuje Date poza tym modułem).
 * Chwila w czasie to `Instant` (milisekundy epoki), a data "na ścianie" to `LocalDate` (YYYY-MM-DD)
 * w strefie Europe/Warsaw, niezależnej od strefy urządzenia.
 */
export const TIME_ZONE = 'Europe/Warsaw';

export type Instant = number & { readonly __brand: 'Instant' };
export type LocalDate = string & { readonly __brand: 'LocalDate' };

const LOCAL_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;
const HH_MM = /^([01]\d|2[0-3]):([0-5]\d)$/;
const inWarsaw = tz(TIME_ZONE);

const asInstant = (ms: number): Instant => ms as Instant;

export function parseInstant(iso: string): Instant {
  const ms = Date.parse(iso);
  if (Number.isNaN(ms)) {
    throw new Error(`Niepoprawny znacznik czasu: ${iso}`);
  }
  return asInstant(ms);
}

export function now(): Instant {
  return asInstant(Date.now());
}

function parts(localDate: LocalDate): [number, number, number] {
  const m = LOCAL_DATE.exec(localDate);
  if (!m) {
    throw new Error(`Niepoprawna data: ${localDate}`);
  }
  return [Number(m[1]), Number(m[2]), Number(m[3])];
}

export function toLocalDate(instant: Instant): LocalDate {
  return format(instant, 'yyyy-MM-dd', { in: inWarsaw }) as LocalDate;
}

/** Dodaje dni kalendarzowe (nie milisekundy), więc jest poprawne w dniach zmiany czasu. */
export function addDays(localDate: LocalDate, days: number): LocalDate {
  const [y, m, d] = parts(localDate);
  const shifted = new Date(Date.UTC(y, m - 1, d + days));
  return shifted.toISOString().slice(0, 10) as LocalDate;
}

/** Początek doby w strefie Warszawy. */
export function startOfDay(localDate: LocalDate): Instant {
  const [y, m, d] = parts(localDate);
  return asInstant(new TZDate(y, m - 1, d, 0, 0, 0, 0, TIME_ZONE).getTime());
}

/** Ostatnia milisekunda doby (początek następnej doby minus 1 ms). */
export function endOfDay(localDate: LocalDate): Instant {
  return asInstant(startOfDay(addDays(localDate, 1)) - 1);
}

/** Zakres półotwarty [from, to) do parametrów zapytań. */
export function dayRange(localDate: LocalDate): { from: Instant; to: Instant } {
  return { from: startOfDay(localDate), to: startOfDay(addDays(localDate, 1)) };
}

/** Łączy datę i godzinę "na ścianie" (HH:mm) w chwilę w strefie Warszawy. */
export function combine(localDate: LocalDate, time: string): Instant {
  const m = HH_MM.exec(time);
  if (!m) {
    throw new Error(`Niepoprawna godzina: ${time}`);
  }
  const [y, mo, d] = parts(localDate);
  return asInstant(new TZDate(y, mo - 1, d, Number(m[1]), Number(m[2]), 0, 0, TIME_ZONE).getTime());
}

export function isSameDay(a: Instant, b: Instant): boolean {
  return toLocalDate(a) === toLocalDate(b);
}

/** Chwile co `stepMinutes` minut w półotwartym zakresie [from, to). */
export function slotsBetween(from: Instant, to: Instant, stepMinutes: number): Instant[] {
  if (stepMinutes <= 0) {
    throw new Error('Krok musi być dodatni');
  }
  const step = stepMinutes * 60_000;
  const slots: Instant[] = [];
  for (let t: number = from; t < to; t += step) {
    slots.push(asInstant(t));
  }
  return slots;
}

export const formatDate = (instant: Instant): string =>
  format(instant, 'dd.MM.yyyy', { in: inWarsaw });
export const formatTime = (instant: Instant): string => format(instant, 'HH:mm', { in: inWarsaw });
export const formatDateTime = (instant: Instant): string =>
  format(instant, 'dd.MM.yyyy HH:mm', { in: inWarsaw });

/** Czas względny po polsku, np. "za 2 dni". */
export function formatRelative(instant: Instant, reference: Instant = now()): string {
  return formatDistance(instant, reference, { addSuffix: true, locale: pl, in: inWarsaw });
}

/** Dzisiejsza data w Warszawie. */
export const today = (): LocalDate => toLocalDate(now());

/** Indeks dnia tygodnia: poniedziałek = 0, niedziela = 6. */
export function weekdayIndex(localDate: LocalDate): number {
  const [y, m, d] = parts(localDate);
  return (new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7;
}

/** Poniedziałek tygodnia, w którym leży data. */
export const weekStart = (localDate: LocalDate): LocalDate =>
  addDays(localDate, -weekdayIndex(localDate));

export function formatLocalDate(localDate: LocalDate): string {
  const [y, m, d] = parts(localDate);
  return `${String(d).padStart(2, '0')}.${String(m).padStart(2, '0')}.${String(y)}`;
}

/** Np. "poniedziałek, 3 listopada". */
export function formatDayLong(localDate: LocalDate): string {
  const [y, m, d] = parts(localDate);
  return format(new TZDate(y, m - 1, d, 12, 0, 0, 0, TIME_ZONE), 'EEEE, d MMMM', {
    locale: pl,
    in: inWarsaw,
  });
}

/** Godzina "na ścianie" bez sekund: "08:00:00" -> "08:00". */
export const shortTime = (time: string): string => time.slice(0, 5);

export function isLocalDate(value: string): value is LocalDate {
  return LOCAL_DATE.test(value);
}
