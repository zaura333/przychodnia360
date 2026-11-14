import { z } from 'zod';

/** Data urodzenia jako YYYY-MM-DD z pola type=date; nie z przyszłości i nie starsza niż 120 lat. */
export function birthDateSchema(today: string) {
  const oldest = `${String(Number(today.slice(0, 4)) - 120)}${today.slice(4)}`;
  return z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Podaj datę urodzenia')
    .refine((d) => d <= today, 'Data urodzenia nie może być z przyszłości')
    .refine((d) => d >= oldest, 'Podaj poprawną datę urodzenia');
}
