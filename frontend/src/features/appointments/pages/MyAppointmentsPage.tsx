import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { AppointmentCard } from '../components/AppointmentCard';
import { useMyAppointments } from '../hooks';
import type { AppointmentView, AppointmentViewStatus } from '@/shared/api/generated/model';
import { now, parseInstant } from '@/shared/lib/datetime';
import { STATUS_LABELS } from '@/shared/lib/appointment-view';
import { Button } from '@/shared/ui/button';
import { Select } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/overlays';
import { QueryState } from '@/shared/ui/query-state';

type StatusFilter = AppointmentViewStatus | '';

function List({ items, empty }: { items: AppointmentView[]; empty: string }) {
  if (items.length === 0) {
    return <p className="rounded-md border border-dashed p-6 text-center">{empty}</p>;
  }
  return (
    <ul className="flex flex-col gap-3">
      {items.map((a) => (
        <li key={a.id}>
          <AppointmentCard
            startAt={a.startAt}
            endAt={a.endAt}
            serviceName={a.serviceName}
            employeeName={a.employeeName}
            status={a.status}
          />
        </li>
      ))}
    </ul>
  );
}

/** Moje wizyty (FR-12): nadchodzące i historia z filtrem statusu. W 1a pacjent nie odwołuje wizyt. */
export function MyAppointmentsPage() {
  const appointments = useMyAppointments();
  const [filter, setFilter] = useState<StatusFilter>('');
  const { upcoming, history } = useMemo(() => {
    const all = appointments.data ?? [];
    const t = now();
    const future = all
      .filter((a) => a.status === 'SCHEDULED' && parseInstant(a.startAt) >= t)
      .sort((a, b) => parseInstant(a.startAt) - parseInstant(b.startAt));
    const past = all
      .filter((a) => !future.includes(a))
      .sort((a, b) => parseInstant(b.startAt) - parseInstant(a.startAt));
    return { upcoming: future, history: past };
  }, [appointments.data]);
  const filteredHistory = filter ? history.filter((a) => a.status === filter) : history;

  return (
    <main className="mx-auto max-w-3xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Moje wizyty</h1>
        <Button asChild>
          <Link to="/pacjent/rezerwacja">Umów wizytę</Link>
        </Button>
      </div>
      <QueryState query={appointments} isEmpty={() => false}>
        {() => (
          <Tabs defaultValue="upcoming">
            <TabsList aria-label="Zakres wizyt">
              <TabsTrigger value="upcoming">Nadchodzące</TabsTrigger>
              <TabsTrigger value="history">Historia</TabsTrigger>
            </TabsList>
            <TabsContent value="upcoming" className="mt-4 flex flex-col gap-3">
              <List items={upcoming} empty="Nie masz zaplanowanych wizyt." />
              {upcoming.length > 0 ? (
                <p className="text-sm text-muted-foreground">
                  Aby odwołać wizytę, skontaktuj się z rejestracją.
                </p>
              ) : null}
            </TabsContent>
            <TabsContent value="history" className="mt-4 flex flex-col gap-3">
              <FormField label="Status">
                <Select
                  value={filter}
                  onChange={(e) => {
                    setFilter(e.target.value as StatusFilter);
                  }}
                >
                  <option value="">Wszystkie</option>
                  {Object.entries(STATUS_LABELS)
                    .filter(([s]) => s !== 'SCHEDULED')
                    .map(([status, label]) => (
                      <option key={status} value={status}>
                        {label}
                      </option>
                    ))}
                </Select>
              </FormField>
              <List items={filteredHistory} empty="Brak wizyt w historii." />
            </TabsContent>
          </Tabs>
        )}
      </QueryState>
    </main>
  );
}
