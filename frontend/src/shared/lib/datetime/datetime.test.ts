import { describe, expect, it } from 'vitest';
import {
  addDays,
  combine,
  dayRange,
  endOfDay,
  formatDate,
  formatDateTime,
  formatRelative,
  formatTime,
  isSameDay,
  parseInstant,
  slotsBetween,
  startOfDay,
  toLocalDate,
  type LocalDate,
} from './index';

const d = (s: string) => s as LocalDate;
const hours = (ms: number) => ms / 3_600_000;
const DST_SPRING = d('2026-03-29'); // doba 23 h, brak godziny 02:00-03:00
const DST_AUTUMN = d('2026-10-25'); // doba 25 h, godzina 02:00 występuje dwa razy

describe('datetime (testy uruchamiane przy TZ innym niż Warszawa)', () => {
  it('runs under a foreign device timezone', () => {
    expect(process.env.TZ).not.toBe('Europe/Warsaw');
  });

  it('shows Warsaw wall-clock time regardless of device zone', () => {
    const i = parseInstant('2026-07-01T08:30:00Z'); // CEST = UTC+2
    expect(formatTime(i)).toBe('10:30');
    expect(formatDate(i)).toBe('01.07.2026');
    expect(formatDateTime(i)).toBe('01.07.2026 10:30');
    expect(formatTime(parseInstant('2026-01-15T08:30:00Z'))).toBe('09:30'); // CET = UTC+1
  });

  it('computes day length in normal and DST days', () => {
    const len = (day: LocalDate) => hours(dayRange(day).to - dayRange(day).from);
    expect(len(d('2026-06-10'))).toBe(24);
    expect(len(DST_SPRING)).toBe(23);
    expect(len(DST_AUTUMN)).toBe(25);
  });

  it('addDays works on calendar days across DST boundaries', () => {
    expect(addDays(d('2026-03-28'), 1)).toBe('2026-03-29');
    expect(addDays(d('2026-03-28'), 2)).toBe('2026-03-30');
    expect(addDays(d('2026-10-24'), 1)).toBe('2026-10-25');
    expect(addDays(d('2026-12-31'), 1)).toBe('2027-01-01');
    expect(addDays(d('2026-03-01'), -1)).toBe('2026-02-28');
    // start doby po przejściu przez zmianę czasu nadal jest o północy czasu warszawskiego
    expect(formatTime(startOfDay(addDays(d('2026-03-28'), 2)))).toBe('00:00');
  });

  it('dayRange is half-open and endOfDay is the last millisecond', () => {
    for (const day of [DST_SPRING, DST_AUTUMN, d('2026-06-10')]) {
      const { from, to } = dayRange(day);
      expect(endOfDay(day)).toBe(to - 1);
      expect(toLocalDate(from)).toBe(day);
      expect(toLocalDate(endOfDay(day))).toBe(day);
      expect(toLocalDate(to)).toBe(addDays(day, 1));
    }
  });

  it('combine builds instants on the wall clock', () => {
    expect(formatDateTime(combine(d('2026-06-10'), '09:15'))).toBe('10.06.2026 09:15');
    expect(formatTime(combine(DST_AUTUMN, '02:30'))).toBe('02:30'); // godzina występuje dwa razy
    expect(() => combine(d('2026-06-10'), '25:00')).toThrow();
  });

  it('spring gap: wall time 02:30 does not exist and resolves forward', () => {
    expect(formatTime(combine(DST_SPRING, '02:30'))).toBe('03:30');
  });

  it('slotsBetween covers a DST day by absolute time', () => {
    const { from, to } = dayRange(DST_SPRING);
    expect(slotsBetween(from, to, 60)).toHaveLength(23);
    const autumn = dayRange(DST_AUTUMN);
    expect(slotsBetween(autumn.from, autumn.to, 60)).toHaveLength(25);
    expect(slotsBetween(autumn.from, autumn.to, 15)).toHaveLength(100);
    expect(() => slotsBetween(from, to, 0)).toThrow();
  });

  it('isSameDay and toLocalDate use Warsaw days, not UTC days', () => {
    const lateEvening = parseInstant('2026-06-10T22:30:00Z'); // 11.06 00:30 w Warszawie
    expect(toLocalDate(lateEvening)).toBe('2026-06-11');
    expect(isSameDay(lateEvening, parseInstant('2026-06-11T10:00:00Z'))).toBe(true);
    expect(isSameDay(lateEvening, parseInstant('2026-06-10T10:00:00Z'))).toBe(false);
  });

  it('formats relative time in Polish', () => {
    const ref = parseInstant('2026-06-10T10:00:00Z');
    expect(formatRelative(parseInstant('2026-06-12T10:00:00Z'), ref)).toBe('za 2 dni');
  });

  it('rejects malformed input', () => {
    expect(() => parseInstant('nie-data')).toThrow();
    expect(() => startOfDay(d('2026-6-1'))).toThrow();
  });
});
