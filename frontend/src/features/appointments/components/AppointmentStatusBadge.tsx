import { CalendarCheck, CheckCircle2, XCircle } from 'lucide-react';
import type { AppointmentViewStatus } from '@/shared/api/generated/model';
import { STATUS_LABELS } from '@/shared/lib/appointment-view';
import { Badge } from '@/shared/ui/display';

const VARIANT = {
  SCHEDULED: 'info',
  COMPLETED: 'success',
  CANCELLED: 'secondary',
} as const;

const ICON = {
  SCHEDULED: CalendarCheck,
  COMPLETED: CheckCircle2,
  CANCELLED: XCircle,
} as const;

/** Status wizyty: ikona i tekst (kolor nie jest jedynym nośnikiem informacji). */
export function AppointmentStatusBadge({ status }: { status: AppointmentViewStatus }) {
  const Icon = ICON[status];
  return (
    <Badge variant={VARIANT[status]} className="gap-1">
      <Icon aria-hidden="true" className="size-3" />
      {STATUS_LABELS[status]}
    </Badge>
  );
}
