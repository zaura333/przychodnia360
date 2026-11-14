import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { useEmployees, useEmployeeSchedule } from '../adminHooks';
import { ExceptionsEditor } from '../components/ExceptionsEditor';
import { WeeklyScheduleEditor } from '../components/WeeklyScheduleEditor';
import { NotFoundPage } from '@/app/pages/pages';
import { ApiError } from '@/shared/api/apiError';
import { today } from '@/shared/lib/datetime';
import { QueryState } from '@/shared/ui/query-state';

/** Grafik pracownika dla administratora (FR-34): edytor grafiku tygodniowego i wyjątków. */
export function EmployeeSchedulePage() {
  const { id } = useParams();
  const [from] = useState(() => today());
  const employees = useEmployees();
  const schedule = useEmployeeSchedule(id, from);
  if (schedule.error instanceof ApiError && schedule.error.status === 404) return <NotFoundPage />;
  const employee = employees.data?.find((e) => e.id === id);
  return (
    <main className="mx-auto flex max-w-5xl flex-col gap-8 p-4">
      <div>
        <Link className="text-sm underline" to="/administracja/pracownicy">
          Wróć do pracowników
        </Link>
        <h1 className="mt-2 text-2xl font-semibold">
          Grafik{employee ? `: ${employee.firstName} ${employee.lastName}` : ''}
        </h1>
      </div>
      <QueryState query={schedule} isEmpty={() => false}>
        {(data) => (
          <>
            <WeeklyScheduleEditor employeeId={id ?? ''} entries={data.weekly} />
            <ExceptionsEditor employeeId={id ?? ''} exceptions={data.exceptions} />
          </>
        )}
      </QueryState>
    </main>
  );
}
