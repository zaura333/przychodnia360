import { zodResolver } from '@hookform/resolvers/zod';
import { useForm, useWatch } from 'react-hook-form';
import { useAddException, useDeleteException } from '../adminHooks';
import { exceptionSchema, type ExceptionFormValues } from '../employeeSchemas';
import type { ExceptionResponse } from '@/shared/api/generated/model';
import { formatLocalDate, parseLocalDate, shortTime, today } from '@/shared/lib/datetime';
import { Button } from '@/shared/ui/button';
import { Input, Select } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

interface ExceptionsEditorProps {
  employeeId: string;
  exceptions: readonly ExceptionResponse[];
}

/** Wyjątki grafiku (FR-34): urlop na cały dzień albo dodatkowa zmiana w godzinach; lista nadchodzących z usuwaniem. */
export function ExceptionsEditor({ employeeId, exceptions }: ExceptionsEditorProps) {
  const add = useAddException(employeeId);
  const remove = useDeleteException(employeeId);
  const notify = useNotify();
  const { register, handleSubmit, formState, control, reset } = useForm<ExceptionFormValues>({
    resolver: zodResolver(exceptionSchema),
    defaultValues: { type: 'ABSENCE', date: today(), startTime: '', endTime: '' },
  });
  const type = useWatch({ control, name: 'type' });
  const sorted = [...exceptions].sort((a, b) => a.date.localeCompare(b.date));

  return (
    <section aria-labelledby="exceptions-heading" className="flex flex-col gap-3">
      <h2 id="exceptions-heading" className="text-xl font-semibold">
        Urlopy i dodatkowe zmiany
      </h2>
      {sorted.length === 0 ? (
        <p className="text-sm text-muted-foreground">Brak nadchodzących wyjątków.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {sorted.map((e) => (
            <li
              key={e.id}
              className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-2"
            >
              <span>
                {formatLocalDate(parseLocalDate(e.date))} ·{' '}
                {e.type === 'ABSENCE'
                  ? 'Urlop'
                  : `Dodatkowa zmiana ${e.startTime ? shortTime(e.startTime) : ''}–${e.endTime ? shortTime(e.endTime) : ''}`}
              </span>
              <Button
                variant="outline"
                aria-label={`Usuń wyjątek z dnia ${formatLocalDate(parseLocalDate(e.date))}`}
                disabled={remove.isPending}
                onClick={() => {
                  remove.mutate(e.id, {
                    onSuccess: () => {
                      notify.success('Usunięto wyjątek');
                    },
                  });
                }}
              >
                Usuń
              </Button>
            </li>
          ))}
        </ul>
      )}
      {remove.isError ? <ErrorMessage error={remove.error} /> : null}
      <form
        aria-label="Nowy wyjątek grafiku"
        className="grid items-end gap-2 sm:grid-cols-5"
        noValidate
        onSubmit={(ev) => {
          void handleSubmit((v) => {
            add.mutate(
              {
                type: v.type,
                date: v.date,
                ...(v.type === 'EXTRA_SHIFT' ? { startTime: v.startTime, endTime: v.endTime } : {}),
              },
              {
                onSuccess: () => {
                  notify.success('Dodano wyjątek');
                  reset({ type: v.type, date: v.date, startTime: '', endTime: '' });
                },
              },
            );
          })(ev);
        }}
      >
        <FormField label="Rodzaj">
          <Select {...register('type')}>
            <option value="ABSENCE">Urlop (cały dzień)</option>
            <option value="EXTRA_SHIFT">Dodatkowa zmiana</option>
          </Select>
        </FormField>
        <FormField label="Data" error={formState.errors.date?.message}>
          <Input type="date" {...register('date')} />
        </FormField>
        {type === 'EXTRA_SHIFT' ? (
          <>
            <FormField label="Od" error={formState.errors.startTime?.message}>
              <Input type="time" {...register('startTime')} />
            </FormField>
            <FormField label="Do" error={formState.errors.endTime?.message}>
              <Input type="time" {...register('endTime')} />
            </FormField>
          </>
        ) : null}
        <Button type="submit" disabled={add.isPending}>
          {add.isPending ? 'Dodawanie…' : 'Dodaj wyjątek'}
        </Button>
      </form>
      {add.isError ? <ErrorMessage error={add.error} /> : null}
    </section>
  );
}
