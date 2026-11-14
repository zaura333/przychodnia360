import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { PasswordField } from '../components/PasswordField';
import { useConfirmPasswordReset } from '../hooks';
import { resetPasswordSchema, type ResetPasswordForm } from '../schemas';
import { AuthShell } from './AuthShell';
import { ApiError } from '@/shared/api/apiError';
import { fieldErrorsOf } from '@/shared/lib/errors';
import { Button } from '@/shared/ui/button';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';

/**
 * Ustawienie hasła z linku (reset i pierwsze hasło pracownika). Token jest usuwany z paska adresu zaraz po
 * wczytaniu i trzymany tylko w stanie komponentu, żeby nie został w historii przeglądarki.
 */
export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [token] = useState(() => params.get('token') ?? '');
  const confirm = useConfirmPasswordReset();
  const { register, handleSubmit, formState, setError } = useForm<ResetPasswordForm>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { password: '', passwordConfirm: '' },
  });

  useEffect(() => {
    if (window.location.search.includes('token')) {
      window.history.replaceState(window.history.state, '', window.location.pathname);
    }
  }, []);

  if (token === '') {
    return (
      <AuthShell title="Ustawienie hasła">
        <p role="alert">Link jest nieprawidłowy.</p>
        <Link className="underline" to="/nie-pamietam-hasla">
          Poproś o nowy link
        </Link>
      </AuthShell>
    );
  }

  const expired =
    confirm.error instanceof ApiError && confirm.error.type === 'invalid-or-expired-token';
  return (
    <AuthShell title="Ustawienie hasła">
      <form
        className="flex flex-col gap-3"
        noValidate
        onSubmit={(e) => {
          void handleSubmit((values) => {
            confirm.mutate(
              { token, newPassword: values.password },
              {
                onSuccess: () => {
                  void navigate('/logowanie', {
                    replace: true,
                    state: { message: 'Hasło zostało ustawione. Możesz się zalogować.' },
                  });
                },
                onError: (error) => {
                  const message = fieldErrorsOf(error).newPassword;
                  if (message) setError('password', { message });
                },
              },
            );
          })(e);
        }}
      >
        <FormField
          label="Nowe hasło"
          hint="Co najmniej 12 znaków."
          error={formState.errors.password?.message}
        >
          <PasswordField autoComplete="new-password" {...register('password')} />
        </FormField>
        <FormField label="Powtórz hasło" error={formState.errors.passwordConfirm?.message}>
          <PasswordField autoComplete="new-password" {...register('passwordConfirm')} />
        </FormField>
        {confirm.isError && !formState.errors.password ? (
          <ErrorMessage error={confirm.error} />
        ) : null}
        {expired ? (
          <Link className="text-sm underline" to="/nie-pamietam-hasla">
            Poproś o nowy link
          </Link>
        ) : null}
        <Button type="submit" disabled={confirm.isPending}>
          {confirm.isPending ? 'Zapisywanie…' : 'Ustaw hasło'}
        </Button>
      </form>
    </AuthShell>
  );
}
