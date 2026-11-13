import type { Instant, LocalDate } from '@/shared/lib/datetime';

export interface ScheduleColumn {
  id: string;
  label: string;
  subLabel?: string | undefined;
  /** Własna doba kolumny (tryb jednego wykonawcy na 7 dni); domyślnie `day` siatki. */
  day?: LocalDate | undefined;
}

export type AppointmentStatus = 'booked' | 'completed' | 'cancelled';

export interface ScheduleAppointment {
  id: string;
  start: Instant;
  end: Instant;
  label: string;
  status: AppointmentStatus;
}

export interface WorkingInterval {
  start: Instant;
  end: Instant;
}

export interface SlotSelection {
  columnId: string;
  start: Instant;
}

export interface ScheduleGridProps {
  columns: readonly ScheduleColumn[];
  /** Doba wyświetlana, gdy kolumna nie ma własnej. */
  day: LocalDate;
  slotMinutes: number;
  /** Granice widoku "HH:mm" (godziny na ścianie w strefie Warszawy). */
  startTime: string;
  endTime: string;
  workingIntervals: Readonly<Record<string, readonly WorkingInterval[]>>;
  appointments: Readonly<Record<string, readonly ScheduleAppointment[]>>;
  selectedAppointmentId?: string | undefined;
  onSlotSelect: (selection: SlotSelection) => void;
  onAppointmentSelect: (id: string) => void;
  loading?: boolean;
}
