import type { ReactNode } from 'react';
import { AppointmentStatusBadge } from './AppointmentStatusBadge';
import type { AppointmentViewStatus } from '@/shared/api/generated/model';
import { formatDayLong, formatTime, parseInstant, toLocalDate } from '@/shared/lib/datetime';

interface AppointmentCardProps {
  startAt: string;
  endAt: string;
  serviceName: string;
  employeeName: string;
  status: AppointmentViewStatus;
  /** Imię i nazwisko pacjenta (tylko widoki pracownicze). */
  patientName?: string | undefined;
  actions?: ReactNode;
}

/** Wizyta: data, godziny, usługa, wykonawca i status; akcje zależą od roli i statusu (przekazuje wywołujący). */
export function AppointmentCard({
  startAt,
  endAt,
  serviceName,
  employeeName,
  status,
  patientName,
  actions,
}: AppointmentCardProps) {
  const start = parseInstant(startAt);
  return (
    <article className="flex flex-wrap items-start justify-between gap-3 rounded-md border border-border p-4">
      <div className="flex flex-col gap-1">
        <h3 className="font-semibold capitalize">{formatDayLong(toLocalDate(start))}</h3>
        <p className="text-sm">
          {formatTime(start)}–{formatTime(parseInstant(endAt))} · {serviceName}
        </p>
        <p className="text-sm text-muted-foreground">
          {employeeName}
          {patientName ? ` · pacjent: ${patientName}` : ''}
        </p>
      </div>
      <div className="flex flex-col items-end gap-2">
        <AppointmentStatusBadge status={status} />
        {actions}
      </div>
    </article>
  );
}
