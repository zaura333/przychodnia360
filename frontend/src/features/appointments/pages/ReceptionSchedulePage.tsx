import { useMemo, useState } from 'react';
import { AppointmentStatusBadge } from '../components/AppointmentStatusBadge';
import { BookForPatientDialog, type BookingTarget } from '../components/BookForPatientDialog';
import { CancelAppointmentDialog } from '../components/CancelAppointmentDialog';
import { RescheduleDialog } from '../components/RescheduleDialog';
import { useReceptionAppointments } from '../hooks';
import { gridHours, toWorkingIntervals, useDoctorSchedules, useDoctors } from '@/features/staff';
import { useAuth } from '@/shared/api/session';
import type { StaffAppointmentView } from '@/shared/api/generated/model';
import {
  addDays,
  formatDayLong,
  formatTime,
  parseInstant,
  today,
  type LocalDate,
} from '@/shared/lib/datetime';
import { toGridAppointment } from '@/shared/lib/appointment-view';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';
import { ScheduleGrid } from '@/shared/ui/schedule-grid/schedule-grid';
import type { ScheduleAppointment, WorkingInterval } from '@/shared/ui/schedule-grid/types';

const REFRESH_MS = 60_000;

/**
 * Terminarz rejestratora (FR-51): kolumny to lekarze, wiersze to kroki slotów. Dane odświeżają się co 60 s,
 * po powrocie do karty i po każdej akcji, bo rejestratorów może być wielu. Wolny slot otwiera umawianie,
 * wizyta otwiera panel z akcjami "Przesuń" i "Odwołaj".
 */
export function ReceptionSchedulePage() {
  const { user } = useAuth();
  const [day, setDay] = useState<LocalDate>(() => today());
  const [hidden, setHidden] = useState<ReadonlySet<string>>(new Set());
  const [showCancelled, setShowCancelled] = useState(false);
  const [target, setTarget] = useState<BookingTarget | null>(null);
  const [selectedId, setSelectedId] = useState<string | undefined>(undefined);
  const [rescheduling, setRescheduling] = useState<StaffAppointmentView | null>(null);
  const [cancelling, setCancelling] = useState<StaffAppointmentView | null>(null);

  const doctors = useDoctors();
  const visible = useMemo(
    () => (doctors.data ?? []).filter((d) => !hidden.has(d.id)),
    [doctors.data, hidden],
  );
  const schedules = useDoctorSchedules(
    visible.map((d) => d.id),
    day,
    day,
  );
  const appointments = useReceptionAppointments({ from: day, to: day }, REFRESH_MS);

  const intervals = useMemo(() => {
    const out: Record<string, WorkingInterval[]> = {};
    visible.forEach((d, i) => {
      out[d.id] = toWorkingIntervals(schedules[i]?.data?.intervals ?? []);
    });
    return out;
  }, [visible, schedules]);

  const all = useMemo(() => appointments.data ?? [], [appointments.data]);
  const byDoctor = useMemo(() => {
    const out: Record<string, ScheduleAppointment[]> = {};
    for (const a of all) {
      (out[a.employeeId] ??= []).push(toGridAppointment(a));
    }
    return out;
  }, [all]);
  const hours = useMemo(() => gridHours(Object.values(intervals).flat()), [intervals]);
  const selected = all.find((a) => a.id === selectedId);
  const cancelled = all.filter((a) => a.status === 'CANCELLED');
  const columns = visible.map((d) => ({
    id: d.id,
    label: `${d.firstName} ${d.lastName}`,
    subLabel: d.specialization,
  }));
  const loading = doctors.isPending || appointments.isPending || schedules.some((s) => s.isPending);
  const failed = doctors.error ?? appointments.error ?? schedules.find((s) => s.error)?.error;

  return (
    <main className="mx-auto max-w-7xl p-4">
      <h1 className="mb-2 text-2xl font-semibold">Terminarz</h1>
      <nav aria-label="Wybór dnia" className="mb-3 flex flex-wrap items-end gap-2">
        <Button
          variant="outline"
          onClick={() => {
            setDay(addDays(day, -1));
          }}
        >
          Poprzedni dzień
        </Button>
        <Button
          variant="outline"
          onClick={() => {
            setDay(today());
          }}
        >
          Dziś
        </Button>
        <Button
          variant="outline"
          onClick={() => {
            setDay(addDays(day, 1));
          }}
        >
          Następny dzień
        </Button>
        <FormField label="Dzień">
          <Input
            type="date"
            value={day}
            onChange={(e) => {
              if (e.target.value) setDay(e.target.value as LocalDate);
            }}
          />
        </FormField>
        <p aria-live="polite" className="text-sm font-medium capitalize">
          {formatDayLong(day)}
        </p>
      </nav>

      <fieldset className="mb-3 flex flex-wrap gap-4">
        <legend className="mb-1 text-sm font-medium">Widoczni lekarze</legend>
        {(doctors.data ?? []).map((d) => (
          <div key={d.id} className="flex items-center gap-2 text-sm">
            <input
              id={`col-${d.id}`}
              type="checkbox"
              checked={!hidden.has(d.id)}
              onChange={(e) => {
                setHidden((prev) => {
                  const next = new Set(prev);
                  if (e.target.checked) next.delete(d.id);
                  else next.add(d.id);
                  return next;
                });
              }}
            />
            <label htmlFor={`col-${d.id}`}>
              {d.firstName} {d.lastName}
            </label>
          </div>
        ))}
        <div className="flex items-center gap-2 text-sm">
          <input
            id="show-cancelled"
            type="checkbox"
            checked={showCancelled}
            onChange={(e) => {
              setShowCancelled(e.target.checked);
            }}
          />
          <label htmlFor="show-cancelled">Pokaż odwołane</label>
        </div>
      </fieldset>

      {failed ? (
        <ErrorMessage
          error={failed}
          onRetry={() => {
            void doctors.refetch();
            void appointments.refetch();
            schedules.forEach((s) => void s.refetch());
          }}
        />
      ) : (
        <ScheduleGrid
          columns={columns}
          day={day}
          slotMinutes={user?.slotStepMinutes ?? 15}
          startTime={hours.startTime}
          endTime={hours.endTime}
          workingIntervals={intervals}
          appointments={byDoctor}
          selectedAppointmentId={selectedId}
          loading={loading}
          onSlotSelect={({ columnId, start }) => {
            const doctor = visible.find((d) => d.id === columnId);
            if (doctor) {
              setTarget({
                employeeId: doctor.id,
                employeeName: `${doctor.firstName} ${doctor.lastName}`,
                start,
              });
            }
          }}
          onAppointmentSelect={setSelectedId}
        />
      )}

      {selected && selected.status !== 'CANCELLED' ? (
        <section
          aria-label="Szczegóły wizyty"
          className="mt-4 flex flex-wrap items-center justify-between gap-3 rounded-md border p-4"
        >
          <div>
            <p className="font-medium">
              {formatTime(parseInstant(selected.startAt))}–
              {formatTime(parseInstant(selected.endAt))} · {selected.serviceName}
            </p>
            <p className="text-sm text-muted-foreground">
              {selected.employeeName} · pacjent: {selected.patientName}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <AppointmentStatusBadge status={selected.status} />
            {selected.status === 'SCHEDULED' ? (
              <>
                <Button
                  variant="outline"
                  onClick={() => {
                    setRescheduling(selected);
                  }}
                >
                  Przesuń
                </Button>
                <Button
                  variant="outline"
                  onClick={() => {
                    setCancelling(selected);
                  }}
                >
                  Odwołaj
                </Button>
              </>
            ) : null}
            <Button
              variant="ghost"
              onClick={() => {
                setSelectedId(undefined);
              }}
            >
              Zamknij
            </Button>
          </div>
        </section>
      ) : null}

      {showCancelled ? (
        <section aria-label="Odwołane wizyty" className="mt-4">
          <h2 className="mb-2 text-lg font-semibold">Odwołane tego dnia</h2>
          {cancelled.length === 0 ? (
            <p className="text-sm text-muted-foreground">Brak odwołanych wizyt.</p>
          ) : (
            <ul className="flex flex-col gap-1 text-sm text-muted-foreground">
              {cancelled.map((a) => (
                <li key={a.id}>
                  {formatTime(parseInstant(a.startAt))} · {a.serviceName} · {a.employeeName} ·{' '}
                  {a.patientName} (odwołana)
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}

      <BookForPatientDialog
        target={target}
        onClose={() => {
          setTarget(null);
        }}
      />
      <RescheduleDialog
        appointment={rescheduling}
        onClose={() => {
          setRescheduling(null);
          setSelectedId(undefined);
        }}
      />
      <CancelAppointmentDialog
        appointment={cancelling}
        onClose={() => {
          setCancelling(null);
          setSelectedId(undefined);
        }}
      />
    </main>
  );
}
