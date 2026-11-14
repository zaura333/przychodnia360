import { z } from 'zod';
import { birthDateSchema } from '@/shared/lib/schemas/birthDate';

/** Pusty adres kontaktowy jest dozwolony (brak adresu), niepusty musi być poprawny. */
const optionalEmail = z.union([z.literal(''), z.email('Niepoprawny adres e-mail')]);

export const contactSchema = z.object({ contactEmail: optionalEmail });
export type ContactForm = z.infer<typeof contactSchema>;

export function createPatientSchema(today: string) {
  return z.object({
    firstName: z.string().trim().min(1, 'Podaj imię').max(100, 'Imię jest zbyt długie'),
    lastName: z.string().trim().min(1, 'Podaj nazwisko').max(100, 'Nazwisko jest zbyt długie'),
    birthDate: birthDateSchema(today),
    contactEmail: optionalEmail,
  });
}
export type CreatePatientForm = z.infer<ReturnType<typeof createPatientSchema>>;

export const MIN_SEARCH_LENGTH = 2;
