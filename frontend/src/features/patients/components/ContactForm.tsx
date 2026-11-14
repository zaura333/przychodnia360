import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { contactSchema, type ContactForm as ContactValues } from '../schemas';
import { ApiError } from '@/shared/api/apiError';
import { fieldErrorsOf } from '@/shared/lib/errors';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';

interface ContactFormProps {
  initialEmail: string;
  onSubmit: (contactEmail: string) => void;
  pending: boolean;
  error: unknown;
  onCancel?: () => void;
}

/** Edycja kontaktowego adresu e-mail (wspólna dla pacjenta i rejestratora). */
export function ContactForm({
  initialEmail,
  onSubmit,
  pending,
  error,
  onCancel,
}: ContactFormProps) {
  const { register, handleSubmit, formState, setError } = useForm<ContactValues>({
    resolver: zodResolver(contactSchema),
    defaultValues: { contactEmail: initialEmail },
  });
  const fieldMessage = fieldErrorsOf(error).contactEmail;
  const conflict = error instanceof ApiError && error.type === 'concurrent-update';
  return (
    <form
      className="flex flex-col gap-3"
      noValidate
      onSubmit={(e) => {
        void handleSubmit(
          (v) => {
            onSubmit(v.contactEmail);
          },
          () => {
            setError('contactEmail', { message: 'Niepoprawny adres e-mail' });
          },
        )(e);
      }}
    >
      <FormField
        label="Adres e-mail do kontaktu"
        error={formState.errors.contactEmail?.message ?? fieldMessage}
      >
        <Input type="email" autoComplete="email" {...register('contactEmail')} />
      </FormField>
      {error && !fieldMessage ? <ErrorMessage error={error} /> : null}
      {conflict ? <p className="text-sm">Odśwież widok, aby pobrać aktualne dane.</p> : null}
      <div className="flex justify-end gap-2">
        {onCancel ? (
          <Button variant="outline" onClick={onCancel}>
            Anuluj
          </Button>
        ) : null}
        <Button type="submit" disabled={pending}>
          {pending ? 'Zapisywanie…' : 'Zapisz'}
        </Button>
      </div>
    </form>
  );
}
