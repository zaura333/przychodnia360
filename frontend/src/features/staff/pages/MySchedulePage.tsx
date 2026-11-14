import { useMemo, useState } from 'react';
import { WeekSchedule } from '../components/WeekSchedule';
import { useMySchedule, weekEnd } from '../hooks';
import { useAuth } from '@/shared/api/session';
import { today, weekStart } from '@/shared/lib/datetime';
import { toGridAppointment } from '@/shared/lib/appointment-view';
import { QueryState } from '@/shared/ui/query-state';
import { WeekNav } from '@/shared/ui/week-nav';

/** Własny grafik lekarza na tydzień (FR-14): przedziały pracy jako tło, wizyty jako bloki. */
export function MySchedulePage() {
  const { user } = useAuth();
  const [start, setStart] = useState(() => weekStart(today()));
  const schedule = useMySchedule(start, weekEnd(start), 60_000);
  const slotMinutes = user?.slotStepMinutes ?? 15;
  const appointments = useMemo(
    () => (schedule.data?.appointments ?? []).map(toGridAppointment),
    [schedule.data],
  );
  return (
    <main className="mx-auto max-w-6xl p-4">
      <h1 className="mb-4 text-2xl font-semibold">Mój grafik</h1>
      <WeekNav start={start} onChange={setStart} />
      <QueryState query={schedule} isEmpty={() => false}>
        {(data) => (
          <WeekSchedule
            start={start}
            title="moje wizyty i godziny pracy"
            intervals={data.intervals}
            appointments={appointments}
            slotMinutes={slotMinutes}
          />
        )}
      </QueryState>
    </main>
  );
}
