import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useCreateTenantAdmin } from '../hooks';
import { tenantAdminSchema, type TenantAdminForm as AdminValues } from '../schemas';
import { ApiError } from '@/shared/api/apiError';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';

interface TenantAdminFormProps {
  tenantId: string;
  onDone: () => void;
}

/**
 * Konto administratora placówki: sam adres e-mail. Administrator dostaje wiadomość z linkiem do ustawienia
 * hasła (bez hasła początkowego), więc w formularzu nie ma pola hasła.
 */
export function TenantAdminForm({ tenantId, onDone }: TenantAdminFormProps) {
  const create = useCreateTenantAdmin();
  const { register, handleSubmit, formState } = useForm<AdminValues>({
    resolver: zodResolver(tenantAdminSchema),
    defaultValues: { email: '' },
  });
  if (create.isSuccess) {
    return (
      <div className="flex flex-col gap-3">
        <p role="status">
          Konto zostało utworzone. Administrator dostanie e-mail z linkiem do ustawienia hasła.
        </p>
        <Button onClick={onDone}>Zamknij</Button>
      </div>
    );
  }
  const taken = create.error instanceof ApiError && create.error.type === 'email-taken';
  return (
    <form
      className="flex flex-col gap-3"
      noValidate
      onSubmit={(e) => {
        void handleSubmit((v) => {
          create.mutate({ tenantId, email: v.email });
        })(e);
      }}
    >
      <FormField
        label="Adres e-mail administratora"
        error={
          formState.errors.email?.message ??
          (taken ? 'Konto z tym adresem już istnieje' : undefined)
        }
      >
        <Input type="email" autoComplete="off" {...register('email')} />
      </FormField>
      {create.isError && !taken ? <ErrorMessage error={create.error} /> : null}
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onDone}>
          Anuluj
        </Button>
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? 'Zapisywanie…' : 'Utwórz konto'}
        </Button>
      </div>
    </form>
  );
}
