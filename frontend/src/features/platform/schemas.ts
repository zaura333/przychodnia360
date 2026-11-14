import { z } from 'zod';

/** Kod placówki zgodny z backendem: małe litery, cyfry i myślniki, 2-63 znaków, nie zaczyna się od myślnika. */
export const slugSchema = z
  .string()
  .regex(
    /^[a-z0-9][a-z0-9-]{1,62}$/,
    'Kod: małe litery, cyfry i myślniki, od 2 do 63 znaków, bez myślnika na początku',
  );

export const tenantSchema = z.object({
  name: z.string().trim().min(1, 'Podaj nazwę placówki').max(200, 'Nazwa jest zbyt długa'),
  slug: slugSchema,
});
export type TenantForm = z.infer<typeof tenantSchema>;

export const tenantAdminSchema = z.object({
  email: z.string().trim().min(1, 'Podaj adres e-mail').pipe(z.email('Niepoprawny adres e-mail')),
});
export type TenantAdminForm = z.infer<typeof tenantAdminSchema>;
