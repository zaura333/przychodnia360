import { useEffect, useMemo, useState } from 'react';
import { useBlocker } from 'react-router';
import { useReplaceSchedule } from '../adminHooks';
import { DAY_LONG, DAY_ORDER } from '../lib';
import {
  entriesFromRows,
  rowsFromEntries,
  validateRows,
  type DayOfWeek,
  type ScheduleRow,
} from '../scheduleRows';
import { ApiError } from '@/shared/api/apiError';
import type { WeeklyEntryResponse } from '@/shared/api/generated/model';
import { today } from '@/shared/lib/datetime';
import { Button } from '@/shared/ui/button';
import { AlertDialog } from '@/shared/ui/dialog';
import { Input, Select } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

interface WeeklyScheduleEditorProps {
  employeeId: string;
  entries: readonly WeeklyEntryResponse[];
}

let counter = 0;
const newKey = () => `new-${String(++counter)}`;

/**
 * Edytor grafiku tygodniowego. PUT zastępuje cały grafik, więc edytor pracuje na całości: lista dyżurów,
 * dodawanie i usuwanie wierszy, zapis jednym przyciskiem. Walidacja kliencka podpowiada na bieżąco,
 * a o nakładaniu się dyżurów ostatecznie rozstrzyga backend.
 */
export function WeeklyScheduleEditor({ employeeId, entries }: WeeklyScheduleEditorProps) {
  const save = useReplaceSchedule(employeeId);
  const notify = useNotify();
  const initial = useMemo(() => rowsFromEntries(entries), [entries]);
  const [rows, setRows] = useState<ScheduleRow[]>(initial);
  const [baseline, setBaseline] = useState(initial);

  // Po odświeżeniu danych z serwera (np. po zapisie) edytor wraca do zgodności z serwerem
  if (initial !== baseline) {
    setBaseline(initial);
    setRows(initial);
  }

  const dirty =
    JSON.stringify(rows.map(({ key: _k, ...r }) => r)) !==
    JSON.stringify(initial.map(({ key: _k, ...r }) => r));
  const errors = validateRows(rows);
  const hasErrors = Object.keys(errors).length > 0;
  const blocker = useBlocker(dirty);

  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', handler);
    return () => {
      window.removeEventListener('beforeunload', handler);
    };
  }, [dirty]);

  const update = (key: string, patch: Partial<ScheduleRow>) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  };
  const conflict = save.error instanceof ApiError && save.error.type === 'schedule-overlap';

  return (
    <section aria-labelledby="weekly-heading" className="flex flex-col gap-3">
      <h2 id="weekly-heading" className="text-xl font-semibold">
        Grafik tygodniowy
      </h2>
      {rows.length === 0 ? (
        <p className="rounded-md border border-dashed p-4 text-sm">
          Brak dyżurów. Dodaj pierwszy dyżur.
        </p>
      ) : null}
      <ul className="flex flex-col gap-3">
        {rows.map((r, index) => {
          const label = `dyżur ${String(index + 1)}`;
          return (
            <li key={r.key} className="rounded-md border p-3">
              <div className="grid items-end gap-2 sm:grid-cols-6">
                <FormField label={`Dzień (${label})`}>
                  <Select
                    value={r.dayOfWeek}
                    onChange={(e) => {
                      update(r.key, { dayOfWeek: e.target.value as DayOfWeek });
                    }}
                  >
                    {DAY_ORDER.map((d) => (
                      <option key={d} value={d}>
                        {DAY_LONG[d]}
                      </option>
                    ))}
                  </Select>
                </FormField>
                <FormField label={`Od (${label})`}>
                  <Input
                    type="time"
                    value={r.startTime}
                    onChange={(e) => {
                      update(r.key, { startTime: e.target.value });
                    }}
                  />
                </FormField>
                <FormField label={`Do (${label})`}>
                  <Input
                    type="time"
                    value={r.endTime}
                    onChange={(e) => {
                      update(r.key, { endTime: e.target.value });
                    }}
                  />
                </FormField>
                <FormField label={`Obowiązuje od (${label})`}>
                  <Input
                    type="date"
                    value={r.validFrom}
                    onChange={(e) => {
                      update(r.key, { validFrom: e.target.value });
                    }}
                  />
                </FormField>
                <FormField label={`Obowiązuje do (${label})`} hint="Puste: bezterminowo.">
                  <Input
                    type="date"
                    value={r.validTo}
                    onChange={(e) => {
                      update(r.key, { validTo: e.target.value });
                    }}
                  />
                </FormField>
                <Button
                  variant="outline"
                  aria-label={`Usuń ${label}`}
                  onClick={() => {
                    setRows((prev) => prev.filter((x) => x.key !== r.key));
                  }}
                >
                  Usuń
                </Button>
              </div>
              {errors[r.key] ? (
                <p role="alert" className="mt-2 text-sm text-destructive">
                  {errors[r.key]}
                </p>
              ) : null}
            </li>
          );
        })}
      </ul>
      {save.isError && !conflict ? <ErrorMessage error={save.error} /> : null}
      {conflict ? (
        <p role="alert" className="rounded-md border border-destructive p-3 text-sm">
          Dyżury nakładają się w tym samym dniu tygodnia. Popraw godziny lub okresy obowiązywania.
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          onClick={() => {
            setRows((prev) => [
              ...prev,
              {
                key: newKey(),
                dayOfWeek: 'MONDAY',
                startTime: '08:00',
                endTime: '16:00',
                validFrom: today(),
                validTo: '',
              },
            ]);
          }}
        >
          Dodaj dyżur
        </Button>
        <Button
          disabled={!dirty || hasErrors || save.isPending}
          onClick={() => {
            save.mutate(entriesFromRows(rows), {
              onSuccess: () => {
                notify.success('Zapisano grafik');
              },
            });
          }}
        >
          {save.isPending ? 'Zapisywanie…' : 'Zapisz grafik'}
        </Button>
        {dirty ? (
          <Button
            variant="ghost"
            onClick={() => {
              setRows(initial);
              save.reset();
            }}
          >
            Cofnij zmiany
          </Button>
        ) : null}
      </div>
      <AlertDialog
        open={blocker.state === 'blocked'}
        title="Niezapisane zmiany"
        description="Masz niezapisane zmiany w grafiku. Jeśli opuścisz stronę, zostaną utracone."
        confirmLabel="Opuść stronę"
        cancelLabel="Zostań"
        destructive
        onCancel={() => {
          blocker.reset?.();
        }}
        onConfirm={() => {
          blocker.proceed?.();
        }}
      />
    </section>
  );
}
