import { useState } from 'react';
import { Link } from 'react-router';
import { useDeactivateEmployee, useEmployees } from '../adminHooks';
import { EmployeeForm } from '../components/EmployeeForm';
import type { EmployeeResponse } from '@/shared/api/generated/model';
import { Badge } from '@/shared/ui/display';
import { Button } from '@/shared/ui/button';
import { DataTable } from '@/shared/ui/data-table';
import { AlertDialog, Dialog } from '@/shared/ui/dialog';
import { ErrorMessage, QueryState } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

const TYPE_LABELS: Record<EmployeeResponse['type'], string> = {
  DOCTOR: 'Lekarz',
  MEDICAL_STAFF: 'Personel medyczny',
  RECEPTIONIST: 'Rejestrator',
};

/** Pracownicy placówki (FR-31): lista, dodanie, edycja, dezaktywacja i przejście do edytora grafiku. */
export function AdminEmployeesPage() {
  const employees = useEmployees();
  const deactivate = useDeactivateEmployee();
  const notify = useNotify();
  const [editing, setEditing] = useState<EmployeeResponse | 'new' | null>(null);
  const [removing, setRemoving] = useState<EmployeeResponse | null>(null);

  return (
    <main className="mx-auto max-w-5xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Pracownicy</h1>
        <Button
          onClick={() => {
            setEditing('new');
          }}
        >
          Dodaj pracownika
        </Button>
      </div>
      {deactivate.isError ? <ErrorMessage error={deactivate.error} /> : null}
      <QueryState
        query={employees}
        emptyTitle="Brak pracowników"
        emptyHint="Dodaj pierwszego pracownika."
      >
        {(data) => (
          <DataTable
            caption="Pracownicy placówki"
            rows={data}
            rowKey={(e) => e.id}
            columns={[
              { header: 'Imię i nazwisko', cell: (e) => `${e.firstName} ${e.lastName}` },
              { header: 'Rola', cell: (e) => TYPE_LABELS[e.type] },
              {
                header: 'Status',
                cell: (e) =>
                  e.active ? (
                    <Badge variant="success">Aktywny</Badge>
                  ) : (
                    <Badge variant="secondary">Nieaktywny</Badge>
                  ),
              },
              {
                header: 'Akcje',
                cell: (e) => (
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="outline"
                      aria-label={`Edytuj pracownika ${e.firstName} ${e.lastName}`}
                      onClick={() => {
                        setEditing(e);
                      }}
                    >
                      Edytuj
                    </Button>
                    {e.type === 'DOCTOR' ? (
                      <Button asChild variant="outline">
                        <Link
                          to={`/administracja/pracownicy/${e.id}/grafik`}
                          aria-label={`Grafik pracownika ${e.firstName} ${e.lastName}`}
                        >
                          Grafik
                        </Link>
                      </Button>
                    ) : null}
                    {e.active ? (
                      <Button
                        variant="outline"
                        aria-label={`Dezaktywuj pracownika ${e.firstName} ${e.lastName}`}
                        onClick={() => {
                          deactivate.reset();
                          setRemoving(e);
                        }}
                      >
                        Dezaktywuj
                      </Button>
                    ) : null}
                  </div>
                ),
              },
            ]}
          />
        )}
      </QueryState>

      <Dialog
        open={editing !== null}
        onClose={() => {
          setEditing(null);
        }}
        title={editing === 'new' ? 'Nowy pracownik' : 'Edycja pracownika'}
      >
        {editing !== null ? (
          <EmployeeForm
            employee={editing === 'new' ? undefined : editing}
            onDone={() => {
              setEditing(null);
            }}
          />
        ) : null}
      </Dialog>
      <AlertDialog
        open={removing !== null}
        title="Dezaktywować pracownika?"
        description="Konto utraci dostęp natychmiast. Pracownika z przyszłymi wizytami nie da się dezaktywować: najpierw trzeba je przesunąć lub odwołać."
        confirmLabel="Dezaktywuj pracownika"
        cancelLabel="Zostaw"
        destructive
        onCancel={() => {
          setRemoving(null);
        }}
        onConfirm={() => {
          if (!removing) return;
          deactivate.mutate(removing.id, {
            onSuccess: () => {
              notify.success('Pracownik został dezaktywowany');
            },
          });
          setRemoving(null);
        }}
      />
    </main>
  );
}
