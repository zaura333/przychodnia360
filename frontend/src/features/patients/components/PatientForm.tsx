import { zodResolver } from '@hookform/resolvers/zod';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useCreatePatient } from '../hooks';
import { createPatientSchema, type CreatePatientForm } from '../schemas';
import type { PatientResponse } from '@/shared/api/generated/model';
import { today } from '@/shared/lib/datetime';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';

interface PatientFormProps {
  onCreated: (patient: PatientResponse) => void;
  onCancel?: () => void;
}

/** Zakładanie pacjenta przez rejestratora (FR-50): imię, nazwisko, data urodzenia, opcjonalny e-mail kontaktowy. */
export function PatientForm({ onCreated, onCancel }: PatientFormProps) {
  const create = useCreatePatient();
  const schema = useMemo(() => createPatientSchema(today()), []);
  const { register, handleSubmit, formState } = useForm<CreatePatientForm>({
    resolver: zodResolver(schema),
    defaultValues: { firstName: '', lastName: '', birthDate: '', contactEmail: '' },
  });
  return (
    <form
      className="flex flex-col gap-3"
      noValidate
      onSubmit={(e) => {
        // Formularz bywa zagnieżdżony w innym oknie: zdarzenie nie może wyzwolić zewnętrznego formularza
        e.stopPropagation();
        void handleSubmit((v) => {
          create.mutate(
            {
              firstName: v.firstName,
              lastName: v.lastName,
              birthDate: v.birthDate,
              ...(v.contactEmail ? { contactEmail: v.contactEmail } : {}),
            },
            { onSuccess: onCreated },
          );
        })(e);
      }}
    >
      <FormField label="Imię" error={formState.errors.firstName?.message}>
        <Input {...register('firstName')} />
      </FormField>
      <FormField label="Nazwisko" error={formState.errors.lastName?.message}>
        <Input {...register('lastName')} />
      </FormField>
      <FormField label="Data urodzenia" error={formState.errors.birthDate?.message}>
        <Input type="date" max={today()} {...register('birthDate')} />
      </FormField>
      <FormField
        label="Adres e-mail do kontaktu"
        hint="Opcjonalnie."
        error={formState.errors.contactEmail?.message}
      >
        <Input type="email" {...register('contactEmail')} />
      </FormField>
      {create.isError ? <ErrorMessage error={create.error} /> : null}
      <div className="flex justify-end gap-2">
        {onCancel ? (
          <Button variant="outline" onClick={onCancel}>
            Anuluj
          </Button>
        ) : null}
        <Button type="submit" disabled={create.isPending}>
          {create.isPending ? 'Zapisywanie…' : 'Zapisz pacjenta'}
        </Button>
      </div>
    </form>
  );
}
