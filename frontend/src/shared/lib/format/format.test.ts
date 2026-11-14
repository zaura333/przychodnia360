import { describe, expect, it } from 'vitest';
import { formatDuration, formatPatientNumber, formatPln, formatPolishPlural } from './index';

const plural = (n: number) => formatPolishPlural(n, 'wizyta', 'wizyty', 'wizyt');

describe('format', () => {
  it('formats PLN from grosze', () => {
    expect(formatPln(1250).replace(/\s/g, ' ')).toBe('12,50 zł');
    expect(formatPln(0).replace(/\s/g, ' ')).toBe('0,00 zł');
  });

  it('formats durations', () => {
    expect(formatDuration(45)).toBe('45 min');
    expect(formatDuration(60)).toBe('1 godz.');
    expect(formatDuration(90)).toBe('1 godz. 30 min');
  });

  it('handles Polish plural forms', () => {
    expect([1, 2, 5, 12, 14, 22, 25, 112].map(plural)).toEqual([
      'wizyta',
      'wizyty',
      'wizyt',
      'wizyt',
      'wizyt',
      'wizyty',
      'wizyt',
      'wizyt',
    ]);
  });

  it('formats patient number', () => {
    expect(formatPatientNumber(123)).toBe('P-000123');
  });
});
