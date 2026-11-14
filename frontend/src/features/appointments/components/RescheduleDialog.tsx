import { useState } from 'react';
import { useReschedule } from '../hooks';
import { classifyBookingError } from '../lib';
import { SlotPicker } from './SlotPicker';
import type { SlotResponse, StaffAppointmentView } from '@/shared/api/generated/model';
import { formatDateTime, parseInstant } from '@/shared/lib/datetime';
import { Button } from '@/shared/ui/button';
import { Dialog } from '@/shared/ui/dialog';
import { ErrorMessage } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

interface RescheduleDialogProps {
  appointment: StaffAppointmentView | null;
  onClose: () => void;
}

function RescheduleBody({
  appointment,
  onClose,
}: {
  appointment: StaffAppointmentView;
  onClose: () => void;
}) {
  const reschedule = useReschedule();
  const notify = useNotify();
  const [slot, setSlot] = useState<SlotResponse | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const failure = reschedule.isError ? classifyBookingError(reschedule.error) : null;

  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm">
        Obecny termin: <strong>{formatDateTime(parseInstant(appointment.startAt))}</strong>,{' '}
        {appointment.serviceName}, {appointment.employeeName}
      </p>
      {notice ? (
        <p role="alert" className="rounded-md border border-destructive p-3 text-sm">
          {notice}
        </p>
      ) : null}
      <SlotPicker
        employeeId={appointment.employeeId}
        serviceId={appointment.serviceId}
        value={slot?.start ?? null}
        onChange={(s) => {
          setSlot(s);
          setNotice(null);
        }}
      />
      {failure?.kind === 'other' ? <ErrorMessage error={reschedule.error} /> : null}
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onClose}>
          Anuluj
        </Button>
        <Button
          disabled={!slot || reschedule.isPending}
          onClick={() => {
            if (!slot) return;
            reschedule.mutate(
              { id: appointment.id, startAt: slot.start },
              {
                onSuccess: () => {
                  notify.success('Wizyta została przesunięta');
                  onClose();
                },
                onError: (error) => {
                  const f = classifyBookingError(error);
                  if (f.kind !== 'other') {
                    // Dialog zostaje otwarty, lista terminów jest odświeżana, wybór terminu czyszczony
                    setNotice(f.message);
                    setSlot(null);
                  }
                },
              },
            );
          }}
        >
          {reschedule.isPending ? 'Przesuwanie…' : 'Przesuń wizytę'}
        </Button>
      </div>
    </div>
  );
}

/** Przesunięcie wizyty: ten sam wybór terminu co w kreatorze; kolizja zostawia dialog otwarty. */
export function RescheduleDialog({ appointment, onClose }: RescheduleDialogProps) {
  return (
    <Dialog open={appointment !== null} onClose={onClose} title="Przesuń wizytę">
      {appointment ? <RescheduleBody appointment={appointment} onClose={onClose} /> : null}
    </Dialog>
  );
}
