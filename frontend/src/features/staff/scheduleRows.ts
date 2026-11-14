import { DAY_ORDER } from './lib';
import type { WeeklyEntryRequest, WeeklyEntryResponse } from '@/shared/api/generated/model';
import { shortTime } from '@/shared/lib/datetime';

export type DayOfWeek = WeeklyEntryResponse['dayOfWeek'];

/** Wiersz edytora grafiku (wartości pól tekstowych: godziny "HH:mm", daty "YYYY-MM-DD", pusty koniec = bezterminowo). */
export interface ScheduleRow {
  key: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  validFrom: string;
  validTo: string;
}

export function rowsFromEntries(entries: readonly WeeklyEntryResponse[]): ScheduleRow[] {
  return [...entries]
    .sort(
      (a, b) =>
        DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek) ||
        a.startTime.localeCompare(b.startTime),
    )
    .map((e) => ({
      key: e.id,
      dayOfWeek: e.dayOfWeek,
      startTime: shortTime(e.startTime),
      endTime: shortTime(e.endTime),
      validFrom: e.validFrom,
      validTo: e.validTo ?? '',
    }));
}

export function entriesFromRows(rows: readonly ScheduleRow[]): WeeklyEntryRequest[] {
  return rows.map((r) => ({
    dayOfWeek: r.dayOfWeek,
    startTime: r.startTime,
    endTime: r.endTime,
    validFrom: r.validFrom,
    ...(r.validTo ? { validTo: r.validTo } : {}),
  }));
}

const FAR = '9999-12-31';

/**
 * Walidacja kliencka (podpowiedź na bieżąco, rozstrzyga backend): wszystkie pola uzupełnione, koniec po
 * początku, okres obowiązywania bez odwróconych dat i brak nakładania się dyżurów tego samego dnia
 * z nakładającym się okresem (dyżury stykające się są dozwolone).
 */
export function validateRows(rows: readonly ScheduleRow[]): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const r of rows) {
    if (!r.startTime || !r.endTime || !r.validFrom) {
      errors[r.key] = 'Uzupełnij godziny i datę początku obowiązywania';
    } else if (r.endTime <= r.startTime) {
      errors[r.key] = 'Koniec dyżuru musi być po jego początku';
    } else if (r.validTo && r.validTo < r.validFrom) {
      errors[r.key] = 'Data końca obowiązywania jest przed datą początku';
    }
  }
  for (let i = 0; i < rows.length; i++) {
    const a = rows[i];
    if (!a || errors[a.key]) continue;
    for (let j = i + 1; j < rows.length; j++) {
      const b = rows[j];
      if (!b || errors[b.key] || a.dayOfWeek !== b.dayOfWeek) continue;
      const timesOverlap = a.startTime < b.endTime && b.startTime < a.endTime;
      const periodsOverlap = a.validFrom <= (b.validTo || FAR) && b.validFrom <= (a.validTo || FAR);
      if (timesOverlap && periodsOverlap) {
        errors[b.key] = 'Dyżury w tym samym dniu nakładają się';
      }
    }
  }
  return errors;
}
