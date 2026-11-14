import { z } from 'zod';
import { birthDateSchema } from '@/shared/lib/schemas/birthDate';
import { passwordSchema } from '@/shared/lib/schemas/password';

const email = z
  .string()
  .trim()
  .min(1, 'Podaj adres e-mail')
  .pipe(z.email('Niepoprawny adres e-mail'));
const tenantSlug = z.string().min(1, 'Wybierz placówkę');

export const loginSchema = z.object({
  tenantSlug,
  email,
  password: z.string().min(1, 'Podaj hasło'),
});
export type LoginForm = z.infer<typeof loginSchema>;

export const platformLoginSchema = loginSchema.omit({ tenantSlug: true });
export type PlatformLoginForm = z.infer<typeof platformLoginSchema>;

export function registerSchema(today: string) {
  return z
    .object({
      tenantSlug,
      email,
      firstName: z.string().trim().min(1, 'Podaj imię').max(100),
      lastName: z.string().trim().min(1, 'Podaj nazwisko').max(100),
      birthDate: birthDateSchema(today),
      password: passwordSchema,
      passwordConfirm: z.string(),
    })
    .refine((v) => v.password === v.passwordConfirm, {
      path: ['passwordConfirm'],
      message: 'Hasła muszą być takie same',
    });
}
export type RegisterForm = z.infer<ReturnType<typeof registerSchema>>;

export const forgotPasswordSchema = z.object({ tenantSlug, email });
export type ForgotPasswordForm = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({ password: passwordSchema, passwordConfirm: z.string() })
  .refine((v) => v.password === v.passwordConfirm, {
    path: ['passwordConfirm'],
    message: 'Hasła muszą być takie same',
  });
export type ResetPasswordForm = z.infer<typeof resetPasswordSchema>;
