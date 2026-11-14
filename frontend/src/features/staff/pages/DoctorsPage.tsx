import { DoctorCard } from '../components/DoctorCard';
import { useDoctors } from '../hooks';
import { QueryState } from '@/shared/ui/query-state';

/** Lista lekarzy placówki z grafikiem (FR-39). */
export function DoctorsPage() {
  const doctors = useDoctors();
  return (
    <main className="mx-auto max-w-4xl p-4">
      <h1 className="mb-4 text-2xl font-semibold">Lekarze</h1>
      <QueryState
        query={doctors}
        emptyTitle="Brak lekarzy"
        emptyHint="Placówka nie ma jeszcze aktywnych lekarzy."
      >
        {(data) => (
          <ul className="flex flex-col gap-3">
            {data.map((d) => (
              <li key={d.id}>
                <DoctorCard doctor={d} />
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </main>
  );
}
