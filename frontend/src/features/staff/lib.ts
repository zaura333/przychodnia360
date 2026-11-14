import type { IntervalResponse, WeeklyEntryResponse } from '@/shared/api/generated/model';
import { minutesOfDay, parseInstant, shortTime } from '@/shared/lib/datetime';
import type { WorkingInterval } from '@/shared/ui/schedule-grid/types';

export const DAY_SHORT: Record<WeeklyEntryResponse['dayOfWeek'], string> = {
  MONDAY: 'Pn',
  TUESDAY: 'Wt',
  WEDNESDAY: 'Śr',
  THURSDAY: 'Czw',
  FRIDAY: 'Pt',
  SATURDAY: 'So',
  SUNDAY: 'Nd',
};

export const DAY_ORDER: readonly WeeklyEntryResponse['dayOfWeek'][] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

export const DAY_LONG: Record<WeeklyEntryResponse['dayOfWeek'], string> = {
  MONDAY: 'Poniedziałek',
  TUESDAY: 'Wtorek',
  WEDNESDAY: 'Środa',
  THURSDAY: 'Czwartek',
  FRIDAY: 'Piątek',
  SATURDAY: 'Sobota',
  SUNDAY: 'Niedziela',
};

/** Skrót grafiku tygodniowego, np. "Pn 08:00–14:00, Śr 10:00–18:00". */
export function weeklySummary(weekly: readonly WeeklyEntryResponse[]): string {
  const sorted = [...weekly].sort(
    (a, b) =>
      DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek) ||
      a.startTime.localeCompare(b.startTime),
  );
  return sorted
    .map((e) => `${DAY_SHORT[e.dayOfWeek]} ${shortTime(e.startTime)}–${shortTime(e.endTime)}`)
    .join(', ');
}

export function toWorkingIntervals(intervals: readonly IntervalResponse[]): WorkingInterval[] {
  return intervals.map((i) => ({ start: parseInstant(i.start), end: parseInstant(i.end) }));
}

const pad = (n: number) => String(n).padStart(2, '0');

/** Granice widoku siatki (pełne godziny) z przedziałów pracy; domyślnie 07:00-19:00. */
export function gridHours(intervals: readonly WorkingInterval[]): {
  startTime: string;
  endTime: string;
} {
  if (intervals.length === 0) return { startTime: '07:00', endTime: '19:00' };
  let min = 24 * 60;
  let max = 0;
  for (const i of intervals) {
    const start = minutesOfDay(i.start);
    let end = minutesOfDay(i.end);
    if (end <= start) end = 24 * 60;
    min = Math.min(min, start);
    max = Math.max(max, end);
  }
  const from = Math.floor(min / 60);
  const to = Math.max(Math.ceil(max / 60), from + 1);
  return { startTime: `${pad(from)}:00`, endTime: `${pad(to)}:00` };
}
