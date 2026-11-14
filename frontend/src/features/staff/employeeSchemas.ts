import { z } from 'zod';

export const employeeSchema = z.object({
  firstName: z.string().trim().min(1, 'Podaj imię').max(100, 'Imię jest zbyt długie'),
  lastName: z.string().trim().min(1, 'Podaj nazwisko').max(100, 'Nazwisko jest zbyt długie'),
  email: z.string().trim().min(1, 'Podaj adres e-mail').pipe(z.email('Niepoprawny adres e-mail')),
  type: z.enum(['DOCTOR', 'MEDICAL_STAFF', 'RECEPTIONIST']),
  specialization: z.string().trim().max(200, 'Specjalizacja jest zbyt długa'),
});
export type EmployeeFormValues = z.infer<typeof employeeSchema>;

/** Edycja nie zmienia adresu e-mail ani roli, więc e-mail nie jest walidowany. */
export const employeeEditSchema = employeeSchema.extend({ email: z.string() });

export const exceptionSchema = z
  .object({
    type: z.enum(['ABSENCE', 'EXTRA_SHIFT']),
    date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Podaj datę'),
    startTime: z.string(),
    endTime: z.string(),
  })
  .superRefine((v, ctx) => {
    if (v.type !== 'EXTRA_SHIFT') return;
    if (!v.startTime)
      ctx.addIssue({ code: 'custom', path: ['startTime'], message: 'Podaj godzinę początku' });
    if (!v.endTime)
      ctx.addIssue({ code: 'custom', path: ['endTime'], message: 'Podaj godzinę końca' });
    if (v.startTime && v.endTime && v.endTime <= v.startTime) {
      ctx.addIssue({ code: 'custom', path: ['endTime'], message: 'Koniec musi być po początku' });
    }
  });
export type ExceptionFormValues = z.infer<typeof exceptionSchema>;
