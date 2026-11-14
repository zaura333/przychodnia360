import { useState } from 'react';
import { Link } from 'react-router';
import { useBook } from '../hooks';
import { classifyBookingError } from '../lib';
import { SlotPicker } from './SlotPicker';
import type { SlotResponse } from '@/shared/api/generated/model';
import { useServices } from '@/features/catalog';
import { useDoctors } from '@/features/staff';
import { formatDayLong, formatTime, parseInstant, toLocalDate } from '@/shared/lib/datetime';
import { formatDuration, formatPrice } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { Skeleton } from '@/shared/ui/display';
import { ErrorMessage, QueryState } from '@/shared/ui/query-state';

type Step = 'service' | 'performer' | 'time' | 'confirm' | 'done';

const STEP_NUMBER: Record<Exclude<Step, 'done'>, number> = {
  service: 1,
  performer: 2,
  time: 3,
  confirm: 4,
};
const STEP_TITLE: Record<Exclude<Step, 'done'>, string> = {
  service: 'Usługa',
  performer: 'Lekarz',
  time: 'Termin',
  confirm: 'Potwierdzenie',
};

interface RadioOptionProps {
  id: string;
  name: string;
  checked: boolean;
  title: string;
  subtitle?: string | undefined;
  onSelect: () => void;
}

/** Opcja wyboru z etykietą powiązaną z polem (id/htmlFor), tytułem i opcjonalnym opisem. */
function RadioOption({ id, name, checked, title, subtitle, onSelect }: RadioOptionProps) {
  return (
    <div className="flex items-center gap-3 rounded-md border p-3">
      <input id={id} type="radio" name={name} checked={checked} onChange={onSelect} />
      <label htmlFor={id} className="flex-1 cursor-pointer">
        <span className="font-medium">{title}</span>
        {subtitle ? <span className="block text-sm text-muted-foreground">{subtitle}</span> : null}
      </label>
    </div>
  );
}

interface BookingWizardProps {
  initialServiceId?: string | undefined;
  initialEmployeeId?: string | undefined;
}

/**
 * Kreator rezerwacji pacjenta: usługa, lekarz, termin, potwierdzenie. Stan trzyma ten komponent; powrót
 * zachowuje wybory, a zmiana usługi lub lekarza czyści wybrany termin. Źródłem prawdy o terminie jest
 * odpowiedź na POST: lista wolnych terminów może być nieaktualna.
 */
export function BookingWizard({ initialServiceId, initialEmployeeId }: BookingWizardProps) {
  const services = useServices({ size: 100 });
  const doctors = useDoctors();
  const book = useBook();
  // Wstępne wypełnienie z adresu; identyfikatory spoza list są odrzucane przy renderowaniu (patrz `current`)
  const [step, setStep] = useState<Step>(
    initialServiceId ? (initialEmployeeId ? 'time' : 'performer') : 'service',
  );
  const [serviceId, setServiceId] = useState<string | undefined>(initialServiceId);
  const [employeeId, setEmployeeId] = useState<string | undefined>(initialEmployeeId);
  const [slot, setSlot] = useState<SlotResponse | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const service = services.data?.items.find((s) => s.id === serviceId);
  const doctor = doctors.data?.find((d) => d.id === employeeId);

  const loaded = services.data !== undefined && doctors.data !== undefined;
  let current: Step = step;
  if (loaded && step !== 'service' && !service) current = 'service';
  else if (loaded && ['time', 'confirm', 'done'].includes(step) && !doctor) current = 'performer';

  const restart = () => {
    setServiceId(undefined);
    setEmployeeId(undefined);
    setSlot(null);
    setStep('service');
  };

  const confirm = () => {
    if (!service || !doctor || !slot) return;
    book.mutate(
      { serviceId: service.id, employeeId: doctor.id, startAt: slot.start },
      {
        onSuccess: () => {
          setStep('done');
        },
        onError: (error) => {
          const failure = classifyBookingError(error);
          if (failure.kind === 'other') return;
          setNotice(failure.message);
          if (failure.kind === 'gone') {
            restart();
          } else {
            setSlot(null);
            setStep('time');
          }
        },
      },
    );
  };

  if (current === 'done' && service && doctor && slot) {
    const day = formatDayLong(toLocalDate(parseInstant(slot.start)));
    return (
      <section aria-labelledby="done-heading" className="flex flex-col gap-3">
        <h2 id="done-heading" className="text-xl font-semibold">
          Wizyta zarezerwowana
        </h2>
        <p>
          {service.name}, {doctor.firstName} {doctor.lastName}
        </p>
        <p>
          {day}, {formatTime(parseInstant(slot.start))}–{formatTime(parseInstant(slot.end))}
        </p>
        <Button asChild className="self-start">
          <Link to="/pacjent/wizyty">Moje wizyty</Link>
        </Button>
      </section>
    );
  }

  const shown: Exclude<Step, 'done'> = current === 'done' ? 'confirm' : current;
  if (!loaded && current !== 'service') {
    return (
      <div role="status" aria-busy="true" aria-label="Ładowanie" className="flex flex-col gap-2">
        <Skeleton className="h-6 w-1/3" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <p aria-live="polite" className="text-sm text-muted-foreground">
        Krok {STEP_NUMBER[shown]} z 4: {STEP_TITLE[shown]}
      </p>
      {notice ? (
        <p role="alert" className="rounded-md border border-destructive p-3 text-sm">
          {notice}
        </p>
      ) : null}

      {current === 'service' ? (
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 font-medium">Wybierz usługę</legend>
          <QueryState
            query={services}
            isEmpty={(d) => d.items.length === 0}
            emptyTitle="Brak dostępnych usług"
          >
            {(data) => (
              <ul className="flex flex-col gap-2">
                {data.items.map((s) => (
                  <li key={s.id}>
                    <RadioOption
                      id={`service-${s.id}`}
                      name="service"
                      checked={serviceId === s.id}
                      title={s.name}
                      subtitle={`${formatPrice(s.price)} · ${formatDuration(s.durationMinutes)}`}
                      onSelect={() => {
                        setServiceId(s.id);
                        setSlot(null);
                        setNotice(null);
                      }}
                    />
                  </li>
                ))}
              </ul>
            )}
          </QueryState>
          <div className="flex justify-end">
            <Button
              disabled={!service}
              onClick={() => {
                setStep('performer');
              }}
            >
              Dalej
            </Button>
          </div>
        </fieldset>
      ) : null}

      {current === 'performer' ? (
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-2 font-medium">Wybierz lekarza</legend>
          <QueryState query={doctors} emptyTitle="Brak lekarzy">
            {(data) => (
              <ul className="flex flex-col gap-2">
                {data.map((d) => (
                  <li key={d.id}>
                    <RadioOption
                      id={`performer-${d.id}`}
                      name="performer"
                      checked={employeeId === d.id}
                      title={`${d.firstName} ${d.lastName}`}
                      subtitle={d.specialization}
                      onSelect={() => {
                        setEmployeeId(d.id);
                        setSlot(null);
                        setNotice(null);
                      }}
                    />
                  </li>
                ))}
              </ul>
            )}
          </QueryState>
          <div className="flex justify-between">
            <Button
              variant="outline"
              onClick={() => {
                setStep('service');
              }}
            >
              Wstecz
            </Button>
            <Button
              disabled={!doctor}
              onClick={() => {
                setStep('time');
              }}
            >
              Dalej
            </Button>
          </div>
        </fieldset>
      ) : null}

      {current === 'time' && service && doctor ? (
        <div className="flex flex-col gap-3">
          <p className="text-sm">
            Czas trwania wizyty: <strong>{formatDuration(service.durationMinutes)}</strong>
            {slot ? `, koniec o ${formatTime(parseInstant(slot.end))}` : ''}
          </p>
          <SlotPicker
            employeeId={doctor.id}
            serviceId={service.id}
            value={slot?.start ?? null}
            onChange={(s) => {
              setSlot(s);
              setNotice(null);
            }}
          />
          <div className="flex justify-between">
            <Button
              variant="outline"
              onClick={() => {
                setStep('performer');
              }}
            >
              Wstecz
            </Button>
            <Button
              disabled={!slot}
              onClick={() => {
                setStep('confirm');
              }}
            >
              Dalej
            </Button>
          </div>
        </div>
      ) : null}

      {current === 'confirm' && service && doctor && slot ? (
        <div className="flex flex-col gap-3">
          <h2 className="text-lg font-semibold">Podsumowanie</h2>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
            <dt className="text-muted-foreground">Usługa</dt>
            <dd>{service.name}</dd>
            <dt className="text-muted-foreground">Cena</dt>
            <dd>{formatPrice(service.price)}</dd>
            <dt className="text-muted-foreground">Lekarz</dt>
            <dd>
              {doctor.firstName} {doctor.lastName}
            </dd>
            <dt className="text-muted-foreground">Termin</dt>
            <dd>
              {formatDayLong(toLocalDate(parseInstant(slot.start)))},{' '}
              {formatTime(parseInstant(slot.start))}–{formatTime(parseInstant(slot.end))}
            </dd>
            <dt className="text-muted-foreground">Czas trwania</dt>
            <dd>{formatDuration(service.durationMinutes)}</dd>
          </dl>
          {book.isError && classifyBookingError(book.error).kind === 'other' ? (
            <ErrorMessage error={book.error} />
          ) : null}
          <div className="flex justify-between">
            <Button
              variant="outline"
              onClick={() => {
                setStep('time');
              }}
            >
              Wstecz
            </Button>
            <Button disabled={book.isPending} onClick={confirm}>
              {book.isPending ? 'Rezerwowanie…' : 'Potwierdzam rezerwację'}
            </Button>
          </div>
        </div>
      ) : null}
    </div>
  );
}
