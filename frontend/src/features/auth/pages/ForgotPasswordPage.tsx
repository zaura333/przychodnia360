import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { rememberedTenant, TenantPicker } from '../components/TenantPicker';
import { useRequestPasswordReset } from '../hooks';
import { forgotPasswordSchema, type ForgotPasswordForm } from '../schemas';
import { AuthShell } from './AuthShell';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';

/** Prośba o reset hasła. Zawsze ten sam komunikat, niezależnie od istnienia konta. */
export function ForgotPasswordPage() {
  const request = useRequestPasswordReset();
  const { register, handleSubmit, formState } = useForm<ForgotPasswordForm>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { tenantSlug: rememberedTenant(), email: '' },
  });

  if (request.isSuccess) {
    return (
      <AuthShell title="Nie pamiętam hasła">
        <p role="status">Jeśli konto istnieje, wysłaliśmy wiadomość z linkiem.</p>
        <Link className="underline" to="/logowanie">
          Wróć do logowania
        </Link>
      </AuthShell>
    );
  }
  return (
    <AuthShell title="Nie pamiętam hasła">
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={(e) => {
          void handleSubmit((values) => {
            request.mutate(values);
          })(e);
        }}
      >
        <FormField label="Placówka" error={formState.errors.tenantSlug?.message}>
          <TenantPicker {...register('tenantSlug')} />
        </FormField>
        <FormField label="Adres e-mail" error={formState.errors.email?.message}>
          <Input type="email" autoComplete="username" {...register('email')} />
        </FormField>
        {request.isError ? <ErrorMessage error={request.error} /> : null}
        <Button type="submit" disabled={request.isPending}>
          {request.isPending ? 'Wysyłanie…' : 'Wyślij link'}
        </Button>
      </form>
      <Link className="text-sm underline" to="/logowanie">
        Wróć do logowania
      </Link>
    </AuthShell>
  );
}
