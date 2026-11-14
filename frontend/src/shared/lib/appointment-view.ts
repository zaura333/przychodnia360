import type { AppointmentViewStatus } from '@/shared/api/generated/model';
import { parseInstant } from '@/shared/lib/datetime';
import type { ScheduleAppointment } from '@/shared/ui/schedule-grid/types';

export const STATUS_LABELS: Record<AppointmentViewStatus, string> = {
  SCHEDULED: 'Zaplanowana',
  CANCELLED: 'Odwołana',
  COMPLETED: 'Wykonana',
};

const GRID_STATUS: Record<AppointmentViewStatus, ScheduleAppointment['status']> = {
  SCHEDULED: 'booked',
  CANCELLED: 'cancelled',
  COMPLETED: 'completed',
};

interface ViewLike {
  id: string;
  startAt: string;
  endAt: string;
  status: AppointmentViewStatus;
  serviceName: string;
  patientName?: string | undefined;
}

/** Wizyta z API jako blok siatki terminarza (etykieta: usługa i pacjent, bez danych medycznych). */
export function toGridAppointment(view: ViewLike): ScheduleAppointment {
  const label = view.patientName ? `${view.serviceName}, ${view.patientName}` : view.serviceName;
  return {
    id: view.id,
    start: parseInstant(view.startAt),
    end: parseInstant(view.endAt),
    label,
    status: GRID_STATUS[view.status],
  };
}
