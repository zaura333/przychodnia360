import { useMemo } from 'react';
import { gridHours, toWorkingIntervals } from '../lib';
import type { IntervalResponse } from '@/shared/api/generated/model';
import { addDays, formatDayLong, type LocalDate } from '@/shared/lib/datetime';
import { ScheduleGrid } from '@/shared/ui/schedule-grid/schedule-grid';
import type { ScheduleAppointment } from '@/shared/ui/schedule-grid/types';

interface WeekScheduleProps {
  /** Poniedziałek wyświetlanego tygodnia. */
  start: LocalDate;
  /** Opis tygodnia dla czytników ekranu, np. imię i nazwisko lekarza. */
  title: string;
  intervals: readonly IntervalResponse[];
  appointments?: readonly ScheduleAppointment[];
  slotMinutes?: number;
  loading?: boolean;
  onAppointmentSelect?: (id: string) => void;
}

/** Tydzień jednego wykonawcy: ta sama siatka co terminarz, w trybie siedmiu kolumn (po jednej na dzień). */
export function WeekSchedule({
  start,
  title,
  intervals,
  appointments = [],
  slotMinutes = 15,
  loading = false,
  onAppointmentSelect,
}: WeekScheduleProps) {
  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(start, i)), [start]);
  const columns = useMemo(
    () => days.map((d) => ({ id: d, label: formatDayLong(d), day: d })),
    [days],
  );
  const working = useMemo(() => toWorkingIntervals(intervals), [intervals]);
  const hours = useMemo(() => gridHours(working), [working]);

  // Przedziały i wizyty trafiają do kolumny dnia, w którym się zaczynają (siatka ma własną dobę kolumny)
  const workingByDay = useMemo(() => {
    const out: Record<string, typeof working> = {};
    for (const d of days) out[d] = working;
    return out;
  }, [days, working]);
  const apptsByDay = useMemo(() => {
    const out: Record<string, ScheduleAppointment[]> = {};
    for (const d of days) out[d] = [...appointments];
    return out;
  }, [days, appointments]);

  return (
    <div role="group" aria-label={`Grafik tygodnia: ${title}`}>
      <ScheduleGrid
        columns={columns}
        day={start}
        slotMinutes={slotMinutes}
        startTime={hours.startTime}
        endTime={hours.endTime}
        workingIntervals={workingByDay}
        appointments={apptsByDay}
        loading={loading}
        onSlotSelect={() => undefined}
        onAppointmentSelect={onAppointmentSelect ?? (() => undefined)}
      />
    </div>
  );
}
