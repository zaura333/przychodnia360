import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { Navigate } from 'react-router';
import { PasswordField } from '../components/PasswordField';
import { usePlatformLogin } from '../hooks';
import { platformLoginSchema, type PlatformLoginForm } from '../schemas';
import { AuthShell } from './AuthShell';
import { defaultAreaFor } from '@/app/areas';
import { useAuth } from '@/shared/api/session';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';

/** Logowanie administratora platformy: bez kodu placówki, bez odnośnika z publicznego logowania. */
export function PlatformLoginPage() {
  const { user } = useAuth();
  const login = usePlatformLogin();
  const { register, handleSubmit, formState } = useForm<PlatformLoginForm>({
    resolver: zodResolver(platformLoginSchema),
    defaultValues: { email: '', password: '' },
  });
  if (user) return <Navigate to={defaultAreaFor(user.role)} replace />;
  return (
    <AuthShell title="Logowanie administratora platformy">
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={(e) => {
          void handleSubmit((values) => {
            login.mutate(values);
          })(e);
        }}
      >
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
    </AuthShell>
  );
}
