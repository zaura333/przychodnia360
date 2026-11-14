import { useMemo, useState } from 'react';
import { AppointmentStatusBadge } from '../components/AppointmentStatusBadge';
import { useReceptionAppointments } from '../hooks';
import { useDoctors } from '@/features/staff';
import type { AppointmentViewStatus } from '@/shared/api/generated/model';
import {
  addDays,
  formatDate,
  formatTime,
  parseInstant,
  today,
  weekStart,
  type LocalDate,
} from '@/shared/lib/datetime';
import { STATUS_LABELS } from '@/shared/lib/appointment-view';
import { DataTable } from '@/shared/ui/data-table';
import { Input, Select } from '@/shared/ui/fields';
import { FormField } from '@/shared/ui/form';
import { Pagination } from '@/shared/ui/pagination';
import { QueryState } from '@/shared/ui/query-state';

const PAGE_SIZE = 20;
/** Okno zapytań backendu: do 32 dni włącznie. */
const MAX_DAYS = 31;

/** Przegląd wizyt placówki dla administratora (tylko odczyt): filtry lekarza, statusu i zakresu dat. */
export function AdminAppointmentsPage() {
  const doctors = useDoctors();
  const [from, setFrom] = useState<LocalDate>(() => weekStart(today()));
  const [to, setTo] = useState<LocalDate>(() => addDays(weekStart(today()), 6));
  const [employeeId, setEmployeeId] = useState('');
  const [status, setStatus] = useState<AppointmentViewStatus | ''>('');
  const [page, setPage] = useState(0);

  const rangeError =
    to < from
      ? 'Data końcowa nie może być wcześniejsza niż początkowa.'
      : to > addDays(from, MAX_DAYS)
        ? 'Zakres może obejmować najwyżej 32 dni.'
        : undefined;
  const validFrom = rangeError ? weekStart(today()) : from;
  const validTo = rangeError ? addDays(weekStart(today()), 6) : to;
  const appointments = useReceptionAppointments({
    from: validFrom,
    to: validTo,
    employeeId: employeeId || undefined,
    status: status || undefined,
  });
  const rows = useMemo(
    () =>
      [...(appointments.data ?? [])].sort(
        (a, b) => parseInstant(a.startAt) - parseInstant(b.startAt),
      ),
    [appointments.data],
  );
  const pageRows = rows.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  return (
    <main className="mx-auto max-w-5xl p-4">
      <h1 className="mb-4 text-2xl font-semibold">Wizyty</h1>
      <form
        aria-label="Filtry wizyt"
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
        onSubmit={(e) => {
          e.preventDefault();
        }}
      >
        <FormField label="Od" error={rangeError}>
          <Input
            type="date"
            value={from}
            onChange={(e) => {
              if (e.target.value) {
                setFrom(e.target.value as LocalDate);
                setPage(0);
              }
            }}
          />
        </FormField>
        <FormField label="Do">
          <Input
            type="date"
            value={to}
            onChange={(e) => {
              if (e.target.value) {
                setTo(e.target.value as LocalDate);
                setPage(0);
              }
            }}
          />
        </FormField>
        <FormField label="Lekarz">
          <Select
            value={employeeId}
            onChange={(e) => {
              setEmployeeId(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Wszyscy</option>
            {(doctors.data ?? []).map((d) => (
              <option key={d.id} value={d.id}>
                {d.firstName} {d.lastName}
              </option>
            ))}
          </Select>
        </FormField>
        <FormField label="Status">
          <Select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as AppointmentViewStatus | '');
              setPage(0);
            }}
          >
            <option value="">Wszystkie</option>
            {Object.entries(STATUS_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </FormField>
      </form>
      <QueryState
        query={appointments}
        isEmpty={(d) => d.length === 0}
        emptyTitle="Brak wizyt w wybranym zakresie"
        emptyHint="Zmień filtry lub zakres dat."
        count={(d) => d.length}
      >
        {() => (
          <>
            <DataTable
              caption="Wizyty placówki"
              rows={pageRows}
              rowKey={(a) => a.id}
              columns={[
                {
                  header: 'Data',
                  cell: (a) => formatDate(parseInstant(a.startAt)),
                },
                {
                  header: 'Godzina',
                  cell: (a) =>
                    `${formatTime(parseInstant(a.startAt))}–${formatTime(parseInstant(a.endAt))}`,
                },
                { header: 'Lekarz', cell: (a) => a.employeeName },
                { header: 'Pacjent', cell: (a) => a.patientName },
                { header: 'Usługa', cell: (a) => a.serviceName },
                { header: 'Status', cell: (a) => <AppointmentStatusBadge status={a.status} /> },
              ]}
            />
            <Pagination page={page} size={PAGE_SIZE} total={rows.length} onPage={setPage} />
          </>
        )}
      </QueryState>
    </main>
  );
}
