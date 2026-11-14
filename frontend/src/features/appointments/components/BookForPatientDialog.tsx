import { useState } from 'react';
import { useBook } from '../hooks';
import { classifyBookingError } from '../lib';
import { useServices } from '@/features/catalog';
import { PatientForm, PatientSearch } from '@/features/patients';
import type { PatientResponse } from '@/shared/api/generated/model';
import {
  formatDayLong,
  formatTime,
  toIsoString,
  toLocalDate,
  type Instant,
} from '@/shared/lib/datetime';
import { formatDuration, formatPrice } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { Dialog } from '@/shared/ui/dialog';
import { Select } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { ErrorMessage } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

export interface BookingTarget {
  employeeId: string;
  employeeName: string;
  start: Instant;
}

interface BookForPatientDialogProps {
  target: BookingTarget | null;
  onClose: () => void;
}

function BookBody({ target, onClose }: { target: BookingTarget; onClose: () => void }) {
  const services = useServices({ size: 100 });
  const book = useBook();
  const notify = useNotify();
  const [serviceId, setServiceId] = useState('');
  const [patient, setPatient] = useState<PatientResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const service = services.data?.items.find((s) => s.id === serviceId);
  const failure = book.isError ? classifyBookingError(book.error) : null;

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm">
        {target.employeeName}, {formatDayLong(toLocalDate(target.start))},{' '}
        {formatTime(target.start)}
      </p>
      {notice ? (
        <p role="alert" className="rounded-md border border-destructive p-3 text-sm">
          {notice}
        </p>
      ) : null}
      <FormField label="Usługa">
        <Select
          value={serviceId}
          onChange={(e) => {
            setServiceId(e.target.value);
            setNotice(null);
          }}
        >
          <option value="">Wybierz usługę</option>
          {(services.data?.items ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {s.name} ({formatDuration(s.durationMinutes)}, {formatPrice(s.price)})
            </option>
          ))}
        </Select>
      </FormField>

      {patient ? (
        <p className="text-sm">
          Pacjent:{' '}
          <strong>
            {patient.firstName} {patient.lastName}
          </strong>{' '}
          <Button
            variant="link"
            onClick={() => {
              setPatient(null);
            }}
          >
            Zmień pacjenta
          </Button>
        </p>
      ) : creating ? (
        <section aria-label="Nowy pacjent">
          <PatientForm
            onCreated={(p) => {
              setPatient(p);
              setCreating(false);
            }}
            onCancel={() => {
              setCreating(false);
            }}
          />
        </section>
      ) : (
        <section aria-label="Wybór pacjenta" className="flex flex-col gap-2">
          <PatientSearch
            renderActions={(p) => (
              <Button
                variant="outline"
                aria-label={`Wybierz pacjenta: ${p.firstName} ${p.lastName}`}
                onClick={() => {
                  setPatient(p);
                }}
              >
                Wybierz
              </Button>
            )}
          />
          <Button
            variant="outline"
            className="self-start"
            onClick={() => {
              setCreating(true);
            }}
          >
            Nowy pacjent
          </Button>
        </section>
      )}

      {failure?.kind === 'other' ? <ErrorMessage error={book.error} /> : null}
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onClose}>
          Anuluj
        </Button>
        <Button
          disabled={!service || !patient || book.isPending}
          onClick={() => {
            if (!service || !patient) return;
            book.mutate(
              {
                serviceId: service.id,
                employeeId: target.employeeId,
                patientId: patient.id,
                startAt: toIsoString(target.start),
              },
              {
                onSuccess: () => {
                  notify.success('Wizyta została umówiona');
                  onClose();
                },
                onError: (error) => {
                  const f = classifyBookingError(error);
                  // Dialog zostaje otwarty z zachowanym wyborem pacjenta i usługi; siatka odświeża się sama
                  if (f.kind !== 'other') setNotice(f.message);
                },
              },
            );
          }}
        >
          {book.isPending ? 'Umawianie…' : 'Umów wizytę'}
        </Button>
      </div>
    </div>
  );
}

/** Umawianie pacjenta z terminarza: usługa, pacjent (wyszukany lub nowy), potwierdzenie. */
export function BookForPatientDialog({ target, onClose }: BookForPatientDialogProps) {
  return (
    <Dialog open={target !== null} onClose={onClose} title="Umów wizytę dla pacjenta">
      {target ? <BookBody target={target} onClose={onClose} /> : null}
    </Dialog>
  );
}
