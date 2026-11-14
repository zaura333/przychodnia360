import { useSearchParams } from 'react-router';
import { BookingWizard } from '../components/BookingWizard';

/** Rezerwacja wizyty (FR-03, FR-13). Wstępne wypełnienie z `?serviceId=` i `?employeeId=` (identyfikatory, nie dane osobowe). */
export function BookAppointmentPage() {
  const [params] = useSearchParams();
  return (
    <main className="mx-auto max-w-2xl p-4">
      <h1 className="mb-4 text-2xl font-semibold">Umów wizytę</h1>
      <BookingWizard
        initialServiceId={params.get('serviceId') ?? undefined}
        initialEmployeeId={params.get('employeeId') ?? undefined}
      />
    </main>
  );
}
