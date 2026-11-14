import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useCreateTenant } from '../hooks';
import { tenantSchema, type TenantForm as TenantValues } from '../schemas';
import { ApiError } from '@/shared/api/apiError';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';

/** Dodanie placówki: nazwa i kod (slug). Zajęty kod (409) to błąd przy polu kodu. */
export function TenantForm({ onDone }: { onDone: () => void }) {
  const create = useCreateTenant();
  const { register, handleSubmit, formState } = useForm<TenantValues>({
    resolver: zodResolver(tenantSchema),
    defaultValues: { name: '', slug: '' },
  });
  const slugTaken = create.error instanceof ApiError && create.error.type === 'slug-taken';
  return (
    <form
      className="flex flex-col gap-3"
      noValidate
      onSubmit={(e) => {
        void handleSubmit((v) => {
          create.mutate(v, { onSuccess: onDone });
        })(e);
      }}
    >
      <FormField label="Nazwa placówki" error={formState.errors.name?.message}>
        <Input {...register('name')} />
      </FormField>
      <FormField
        label="Kod placówki"
        hint="Używany przy logowaniu, np. klinika-c."
        error={
          formState.errors.slug?.message ?? (slugTaken ? 'Ten kod jest już zajęty' : undefined)
        }
      >
        <Input autoCapitalize="none" autoComplete="off" {...register('slug')} />
      </FormField>
      {create.isError && !slugTaken ? <ErrorMessage error={create.error} /> : null}
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onDone}>
          Anuluj
        </Button>
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? 'Zapisywanie…' : 'Dodaj placówkę'}
        </Button>
      </div>
    </form>
  );
}
