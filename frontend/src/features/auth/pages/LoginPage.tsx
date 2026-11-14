import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Link, Navigate, useLocation } from 'react-router';
import { PasswordField } from '../components/PasswordField';
import { rememberedTenant, TenantPicker } from '../components/TenantPicker';
import { useLogin } from '../hooks';
import { loginSchema, type LoginForm } from '../schemas';
import { AuthShell } from './AuthShell';
import { defaultAreaFor } from '@/app/areas';
import { useAuth } from '@/shared/api/session';
import { isSafeInternalPath } from '@/shared/lib/safe-path';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';

interface LocationState {
  from?: unknown;
  message?: unknown;
}

/** Logowanie do placówki. Zalogowany użytkownik jest przekierowywany do obszaru roli lub zapamiętanej ścieżki. */
export function LoginPage() {
  const { user, backendUnavailable } = useAuth();
  const location = useLocation();
  const state = (location.state ?? {}) as LocationState;
  const login = useLogin();
  const { register, handleSubmit, formState } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { tenantSlug: rememberedTenant(), email: '', password: '' },
  });

  if (user) {
    const target = isSafeInternalPath(state.from) ? state.from : defaultAreaFor(user.role);
    return <Navigate to={target} replace />;
  }

  return (
    <AuthShell title="Logowanie">
      {typeof state.message === 'string' ? (
        <p role="status" className="rounded-md border border-border p-3 text-sm">
          {state.message}
        </p>
      ) : null}
      {backendUnavailable ? (
        <p role="alert" className="rounded-md border border-destructive p-3 text-destructive">
          Nie można połączyć z serwerem. Spróbuj ponownie za chwilę.
        </p>
      ) : null}
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={(e) => {
          void handleSubmit((values) => {
            login.mutate(values);
          })(e);
        }}
      >
        <FormField label="Placówka" error={formState.errors.tenantSlug?.message}>
          <TenantPicker {...register('tenantSlug')} />
        </FormField>
        <FormField label="Adres e-mail" error={formState.errors.email?.message}>
          <Input type="email" autoComplete="username" {...register('email')} />
        </FormField>
        <FormField label="Hasło" error={formState.errors.password?.message}>
          <PasswordField autoComplete="current-password" {...register('password')} />
        </FormField>
        {login.isError ? <ErrorMessage error={login.error} /> : null}
        <Button type="submit" disabled={login.isPending}>
          {login.isPending ? 'Logowanie…' : 'Zaloguj się'}
        </Button>
      </form>
      <nav aria-label="Pozostałe działania" className="flex flex-col gap-1 text-sm">
        <Link className="underline" to="/rejestracja">
          Załóż konto pacjenta
        </Link>
        <Link className="underline" to="/nie-pamietam-hasla">
          Nie pamiętam hasła
        </Link>
      </nav>
    </AuthShell>
  );
}
