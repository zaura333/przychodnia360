import { zodResolver } from '@hookform/resolvers/zod';
import { useForm, useWatch } from 'react-hook-form';
import { useCreateEmployee, useUpdateEmployee } from '../adminHooks';
import { employeeEditSchema, employeeSchema, type EmployeeFormValues } from '../employeeSchemas';
import { ApiError } from '@/shared/api/apiError';
import type { EmployeeResponse } from '@/shared/api/generated/model';
import { env } from '@/shared/config/env';
import { Button } from '@/shared/ui/button';
import { Input, Select } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

interface EmployeeFormProps {
  /** Brak: dodawanie pracownika; podany: edycja imienia, nazwiska i specjalizacji. */
  employee?: EmployeeResponse | undefined;
  onDone: () => void;
}

/**
 * Konto pracownika powstaje bez hasła: pracownik dostaje e-mail z linkiem do ustawienia hasła (72 h),
 * więc formularz nie ma pola hasła. Typ "personel medyczny" jest ukryty flagą (1d).
 */
export function EmployeeForm({ employee, onDone }: EmployeeFormProps) {
  const create = useCreateEmployee();
  const update = useUpdateEmployee();
  const notify = useNotify();
  const editing = employee !== undefined;
  const { register, handleSubmit, formState, control } = useForm<EmployeeFormValues>({
    resolver: zodResolver(editing ? employeeEditSchema : employeeSchema),
    defaultValues: {
      firstName: employee?.firstName ?? '',
      lastName: employee?.lastName ?? '',
      email: '',
      type: employee?.type ?? 'DOCTOR',
      specialization: employee?.specialization ?? '',
    },
  });
  const type = useWatch({ control, name: 'type' });
  const mutation = editing ? update : create;
  const emailTaken = create.error instanceof ApiError && create.error.type === 'email-taken';

  return (
    <form
      className="flex flex-col gap-3"
      noValidate
      onSubmit={(e) => {
        void handleSubmit((v) => {
          const specialization =
            v.type === 'DOCTOR' && v.specialization ? v.specialization : undefined;
          if (employee) {
            update.mutate(
              {
                id: employee.id,
                body: {
                  firstName: v.firstName,
                  lastName: v.lastName,
                  ...(specialization ? { specialization } : {}),
                },
              },
              {
                onSuccess: () => {
                  notify.success('Zapisano zmiany pracownika');
                  onDone();
                },
              },
            );
          } else {
            create.mutate(
              {
                firstName: v.firstName,
                lastName: v.lastName,
                email: v.email,
                type: v.type,
                ...(specialization ? { specialization } : {}),
              },
              {
                onSuccess: () => {
                  notify.success(
                    'Dodano pracownika. Dostanie e-mail z linkiem do ustawienia hasła.',
                  );
                  onDone();
                },
              },
            );
          }
        })(e);
      }}
    >
      <FormField label="Imię" error={formState.errors.firstName?.message}>
        <Input {...register('firstName')} />
      </FormField>
      <FormField label="Nazwisko" error={formState.errors.lastName?.message}>
        <Input {...register('lastName')} />
      </FormField>
      {editing ? null : (
        <>
          <FormField
            label="Adres e-mail"
            hint="Pracownik dostanie na ten adres link do ustawienia hasła."
            error={
              formState.errors.email?.message ??
              (emailTaken ? 'Konto z tym adresem już istnieje' : undefined)
            }
          >
            <Input type="email" autoComplete="off" {...register('email')} />
          </FormField>
          <FormField label="Rola" error={formState.errors.type?.message}>
            <Select {...register('type')}>
              <option value="DOCTOR">Lekarz</option>
              <option value="RECEPTIONIST">Rejestrator</option>
              {env.featureMedicalStaff ? (
                <option value="MEDICAL_STAFF">Personel medyczny</option>
              ) : null}
            </Select>
          </FormField>
        </>
      )}
      {type === 'DOCTOR' ? (
        <FormField label="Specjalizacja" error={formState.errors.specialization?.message}>
          <Input {...register('specialization')} />
        </FormField>
      ) : null}
      {mutation.isError && !emailTaken ? <ErrorMessage error={mutation.error} /> : null}
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onDone}>
          Anuluj
        </Button>
        <Button type="submit" disabled={mutation.isPending}>
          {mutation.isPending ? 'Zapisywanie…' : 'Zapisz'}
        </Button>
      </div>
    </form>
  );
}
