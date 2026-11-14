import { z } from 'zod';

export const MIN_DURATION = 5;
export const MAX_DURATION = 480;

export const serviceSchema = z.object({
  name: z.string().trim().min(1, 'Podaj nazwę usługi').max(200, 'Nazwa jest zbyt długa'),
  description: z.string().max(2000, 'Opis jest zbyt długi'),
  categoryId: z.string().min(1, 'Wybierz kategorię'),
  /** Cena w groszach (liczba całkowita). */
  priceGrosze: z.number().int().min(0, 'Podaj cenę'),
  durationMinutes: z
    .number({ error: 'Podaj czas trwania' })
    .int('Podaj liczbę całkowitą')
    .min(MIN_DURATION, `Czas trwania: od ${String(MIN_DURATION)} minut`)
    .max(MAX_DURATION, `Czas trwania: do ${String(MAX_DURATION)} minut`),
  performerType: z.enum(['DOCTOR', 'MEDICAL_STAFF']),
});
export type ServiceFormValues = z.infer<typeof serviceSchema>;

export const categorySchema = z.object({
  name: z.string().trim().min(1, 'Podaj nazwę kategorii').max(100, 'Nazwa jest zbyt długa'),
});
