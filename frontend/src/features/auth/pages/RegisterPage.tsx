import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import { PasswordField } from '../components/PasswordField';
import { rememberedTenant, TenantPicker } from '../components/TenantPicker';
import { useRegisterPatient } from '../hooks';
import { registerSchema, type RegisterForm } from '../schemas';
import { AuthShell } from './AuthShell';
import { today } from '@/shared/lib/datetime';
import { fieldErrorsOf } from '@/shared/lib/errors';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';

/** Rejestracja pacjenta. Odpowiedź backendu jest zawsze neutralna (202), więc nie ujawniamy zajętego adresu. */
export function RegisterPage() {
  const signUp = useRegisterPatient();
  const schema = useMemo(() => registerSchema(today()), []);
  const { register, handleSubmit, formState, setError } = useForm<RegisterForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      tenantSlug: rememberedTenant(),
      email: '',
      firstName: '',
      lastName: '',
      birthDate: '',
      password: '',
      passwordConfirm: '',
    },
  });

  if (signUp.isSuccess) {
    return (
      <AuthShell title="Rejestracja">
        <p role="status">Jeśli adres jest wolny, konto zostało utworzone, możesz się zalogować.</p>
        <Link className="underline" to="/logowanie">
          Przejdź do logowania
        </Link>
      </AuthShell>
    );
  }

  const hasFieldErrors = Object.keys(fieldErrorsOf(signUp.error)).length > 0;
  return (
    <AuthShell title="Rejestracja pacjenta">
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={(e) => {
          void handleSubmit((values) => {
            signUp.mutate(values, {
              onError: (error) => {
                for (const [name, message] of Object.entries(fieldErrorsOf(error))) {
                  if (name in values) setError(name as keyof RegisterForm, { message });
                }
              },
            });
          })(e);
        }}
      >
        <FormField label="Placówka" error={formState.errors.tenantSlug?.message}>
          <TenantPicker {...register('tenantSlug')} />
        </FormField>
        <FormField label="Imię" error={formState.errors.firstName?.message}>
          <Input autoComplete="given-name" {...register('firstName')} />
        </FormField>
        <FormField label="Nazwisko" error={formState.errors.lastName?.message}>
          <Input autoComplete="family-name" {...register('lastName')} />
        </FormField>
        <FormField label="Data urodzenia" error={formState.errors.birthDate?.message}>
          <Input type="date" max={today()} autoComplete="bday" {...register('birthDate')} />
        </FormField>
        <FormField label="Adres e-mail" error={formState.errors.email?.message}>
          <Input type="email" autoComplete="email" {...register('email')} />
        </FormField>
        <FormField
          label="Hasło"
          hint="Co najmniej 12 znaków."
          error={formState.errors.password?.message}
        >
          <PasswordField autoComplete="new-password" {...register('password')} />
        </FormField>
        <FormField label="Powtórz hasło" error={formState.errors.passwordConfirm?.message}>
          <PasswordField autoComplete="new-password" {...register('passwordConfirm')} />
        </FormField>
        {signUp.isError && !hasFieldErrors ? <ErrorMessage error={signUp.error} /> : null}
        <Button type="submit" disabled={signUp.isPending}>
          {signUp.isPending ? 'Wysyłanie…' : 'Załóż konto'}
        </Button>
      </form>
      <Link className="text-sm underline" to="/logowanie">
        Mam już konto
      </Link>
    </AuthShell>
  );
}
