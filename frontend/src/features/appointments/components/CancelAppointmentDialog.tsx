import { useCancel } from '../hooks';
import type { StaffAppointmentView } from '@/shared/api/generated/model';
import { formatDateTime, parseInstant } from '@/shared/lib/datetime';
import { Button } from '@/shared/ui/button';
import { Dialog } from '@/shared/ui/dialog';
import { ErrorMessage } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

interface CancelAppointmentDialogProps {
  appointment: StaffAppointmentView | null;
  onClose: () => void;
}

/** Odwołanie wizyty przez rejestratora (bez ograniczenia 24 h): potwierdzenie z opisem skutku. */
export function CancelAppointmentDialog({ appointment, onClose }: CancelAppointmentDialogProps) {
  const cancel = useCancel();
  const notify = useNotify();
  return (
    <Dialog
      open={appointment !== null}
      onClose={onClose}
      role="alertdialog"
      title="Odwołać wizytę?"
      description={
        appointment
          ? `${appointment.serviceName}, ${appointment.patientName}, ${formatDateTime(parseInstant(appointment.startAt))}. Termin wróci do puli wolnych terminów.`
          : ''
      }
    >
      {cancel.isError ? <ErrorMessage error={cancel.error} /> : null}
      <div className="mt-3 flex justify-end gap-2">
        <Button
          variant="outline"
          onClick={() => {
            cancel.reset();
            onClose();
          }}
        >
          Zostaw
        </Button>
        <Button
          variant="destructive"
          disabled={cancel.isPending}
          onClick={() => {
            if (!appointment) return;
            cancel.mutate(appointment.id, {
              onSuccess: () => {
                notify.success('Wizyta została odwołana');
                onClose();
              },
            });
          }}
        >
          {cancel.isPending ? 'Odwoływanie…' : 'Odwołaj wizytę'}
        </Button>
      </div>
    </Dialog>
  );
}
