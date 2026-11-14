import { useState } from 'react';
import { Link } from 'react-router';
import { useDoctorSchedule, weekEnd } from '../hooks';
import { weeklySummary } from '../lib';
import { WeekSchedule } from './WeekSchedule';
import type { DoctorResponse } from '@/shared/api/generated/model';
import { today, weekStart } from '@/shared/lib/datetime';
import { Button } from '@/shared/ui/button';
import { QueryState } from '@/shared/ui/query-state';
import { WeekNav } from '@/shared/ui/week-nav';

function ScheduleView({ doctor }: { doctor: DoctorResponse }) {
  const [start, setStart] = useState(() => weekStart(today()));
  const schedule = useDoctorSchedule(doctor.id, start, weekEnd(start));
  return (
    <div className="mt-3">
      <WeekNav start={start} onChange={setStart} />
      <QueryState query={schedule} isEmpty={() => false}>
        {(data) => (
          <WeekSchedule
            start={start}
            title={`${doctor.firstName} ${doctor.lastName}`}
            intervals={data.intervals}
          />
        )}
      </QueryState>
    </div>
  );
}

/** Karta lekarza: imię i nazwisko, specjalizacja, skrót grafiku, podgląd tygodnia i przejście do rezerwacji. */
export function DoctorCard({ doctor }: { doctor: DoctorResponse }) {
  const [open, setOpen] = useState(false);
  const summary = weeklySummary(doctor.weekly);
  return (
    <article className="rounded-md border border-border p-4">
      <h2 className="text-lg font-semibold">
        {doctor.firstName} {doctor.lastName}
      </h2>
      {doctor.specialization ? (
        <p className="text-sm text-muted-foreground">{doctor.specialization}</p>
      ) : null}
      <p className="mt-1 text-sm">{summary || 'Brak grafiku'}</p>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button
          variant="outline"
          aria-expanded={open}
          aria-label={`${open ? 'Ukryj' : 'Pokaż'} grafik: ${doctor.firstName} ${doctor.lastName}`}
          onClick={() => {
            setOpen((o) => !o);
          }}
        >
          {open ? 'Ukryj grafik' : 'Pokaż grafik'}
        </Button>
        <Button asChild>
          <Link to={`/pacjent/rezerwacja?employeeId=${doctor.id}`}>Umów do tego lekarza</Link>
        </Button>
      </div>
      {open ? <ScheduleView doctor={doctor} /> : null}
    </article>
  );
}
