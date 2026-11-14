import { useMemo } from 'react';
import { AppointmentCard } from '../components/AppointmentCard';
import { useMySchedule } from '@/features/staff';
import { formatDayLong, parseInstant, today } from '@/shared/lib/datetime';
import { QueryState } from '@/shared/ui/query-state';

/** Dzienna lista wizyt lekarza (bez treści medycznych): godzina, usługa, pacjent, status. */
export function DoctorDayPage() {
  const day = today();
  const schedule = useMySchedule(day, day, 60_000);
  const appointments = useMemo(
    () =>
      [...(schedule.data?.appointments ?? [])].sort(
        (a, b) => parseInstant(a.startAt) - parseInstant(b.startAt),
      ),
    [schedule.data],
  );
  return (
    <main className="mx-auto max-w-3xl p-4">
      <h1 className="text-2xl font-semibold">Dzisiaj</h1>
      <p className="mb-4 text-sm capitalize text-muted-foreground">{formatDayLong(day)}</p>
      <QueryState
        query={{
          ...schedule,
          data: appointments.length === 0 && !schedule.data ? undefined : appointments,
        }}
        emptyTitle="Nie masz dziś wizyt"
      >
        {(list) => (
          <ul className="flex flex-col gap-3">
            {list.map((a) => (
              <li key={a.id}>
                <AppointmentCard
                  startAt={a.startAt}
                  endAt={a.endAt}
                  serviceName={a.serviceName}
                  employeeName={a.employeeName}
                  status={a.status}
                  patientName={a.patientName}
                />
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </main>
  );
}
