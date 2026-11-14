import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm, type Control } from 'react-hook-form';
import { useSaveService } from '../hooks';
import { PERFORMER_LABELS } from '../labels';
import { serviceSchema, type ServiceFormValues } from '../schemas';
import { PriceInput } from './PriceInput';
import { ApiError } from '@/shared/api/apiError';
import type { CategoryResponse, ServiceResponse } from '@/shared/api/generated/model';
import { env } from '@/shared/config/env';
import { fieldErrorsOf } from '@/shared/lib/errors';
import { groszeToPln, plnToGrosze } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { Input, Select, Textarea } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

/** Pole ceny jako własny komponent, żeby FormField mógł nadać mu id (Controller go nie przekazuje dalej). */
function PriceField({
  control,
  id,
  'aria-describedby': describedBy,
  'aria-invalid': invalid,
}: {
  control: Control<ServiceFormValues>;
  id?: string;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean;
}) {
  return (
    <Controller
      control={control}
      name="priceGrosze"
      render={({ field }) => (
        <PriceInput
          {...(id ? { id } : {})}
          {...(describedBy ? { 'aria-describedby': describedBy } : {})}
          {...(invalid ? { 'aria-invalid': invalid } : {})}
          value={field.value}
          onChange={field.onChange}
        />
      )}
    />
  );
}

interface ServiceFormProps {
  service?: ServiceResponse | undefined;
  categories: readonly CategoryResponse[];
  onDone: () => void;
}

export function ServiceForm({ service, categories, onDone }: ServiceFormProps) {
  const save = useSaveService();
  const notify = useNotify();
  const { register, control, handleSubmit, formState, setError } = useForm<ServiceFormValues>({
    resolver: zodResolver(serviceSchema),
    defaultValues: {
      name: service?.name ?? '',
      description: service?.description ?? '',
      categoryId: service?.categoryId ?? '',
      priceGrosze: service ? plnToGrosze(service.price) : 0,
      durationMinutes: service?.durationMinutes ?? 30,
      performerType: service?.performerType ?? 'DOCTOR',
    },
  });
  const conflict = save.error instanceof ApiError && save.error.type === 'concurrent-update';
  const serverFields = fieldErrorsOf(save.error);

  return (
    <form
      className="flex flex-col gap-3"
      noValidate
      onSubmit={(e) => {
        void handleSubmit((v) => {
          save.mutate(
            {
              id: service?.id,
              body: {
                name: v.name,
                description: v.description,
                categoryId: v.categoryId,
                price: groszeToPln(v.priceGrosze),
                durationMinutes: v.durationMinutes,
                performerType: v.performerType,
              },
            },
            {
              onSuccess: () => {
                notify.success(service ? 'Zapisano zmiany usługi' : 'Dodano usługę');
                onDone();
              },
              onError: (error) => {
                for (const [name, message] of Object.entries(fieldErrorsOf(error))) {
                  if (name in v) setError(name as keyof ServiceFormValues, { message });
                }
              },
            },
          );
        })(e);
      }}
    >
      <FormField label="Nazwa" error={formState.errors.name?.message}>
        <Input {...register('name')} />
      </FormField>
      <FormField label="Opis" error={formState.errors.description?.message}>
        <Textarea {...register('description')} />
      </FormField>
      <FormField label="Kategoria" error={formState.errors.categoryId?.message}>
        <Select {...register('categoryId')}>
          <option value="">Wybierz kategorię</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </Select>
      </FormField>
      <FormField label="Cena" error={formState.errors.priceGrosze?.message}>
        <PriceField control={control} />
      </FormField>
      <FormField
        label="Czas trwania (minuty)"
        hint="Od 5 do 480 minut."
        error={formState.errors.durationMinutes?.message}
      >
        <Input
          type="number"
          min={5}
          max={480}
          {...register('durationMinutes', { valueAsNumber: true })}
        />
      </FormField>
      {env.featureMedicalStaff ? (
        <FormField label="Wykonawca" error={formState.errors.performerType?.message}>
          <Select {...register('performerType')}>
            {Object.entries(PERFORMER_LABELS).map(([type, label]) => (
              <option key={type} value={type}>
                {label}
              </option>
            ))}
          </Select>
        </FormField>
      ) : null}
      {save.isError && Object.keys(serverFields).length === 0 ? (
        <ErrorMessage error={save.error} />
      ) : null}
      <div className="flex justify-end gap-2">
        {conflict ? (
          <Button variant="outline" onClick={onDone}>
            Przeładuj dane
          </Button>
        ) : null}
        <Button variant="outline" onClick={onDone}>
          Anuluj
        </Button>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending ? 'Zapisywanie…' : 'Zapisz'}
        </Button>
      </div>
    </form>
  );
}
