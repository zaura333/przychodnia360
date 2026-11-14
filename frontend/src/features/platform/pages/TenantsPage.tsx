import { useState } from 'react';
import { TenantAdminForm } from '../components/TenantAdminForm';
import { TenantForm } from '../components/TenantForm';
import { useSetTenantActive, useTenants } from '../hooks';
import type { TenantResponse } from '@/shared/api/generated/model';
import { Badge } from '@/shared/ui/display';
import { Button } from '@/shared/ui/button';
import { DataTable } from '@/shared/ui/data-table';
import { AlertDialog, Dialog } from '@/shared/ui/dialog';
import { ErrorMessage, QueryState } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

/**
 * Placówki platformy (FR-43): lista, dodanie, dezaktywacja i konto administratora placówki. Obszar nie
 * pobiera żadnych innych danych (platforma nie widzi danych placówek).
 */
export function TenantsPage() {
  const tenants = useTenants();
  const setActive = useSetTenantActive();
  const notify = useNotify();
  const [creating, setCreating] = useState(false);
  const [adminFor, setAdminFor] = useState<TenantResponse | null>(null);
  const [deactivating, setDeactivating] = useState<TenantResponse | null>(null);

  return (
    <main className="mx-auto max-w-4xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Placówki</h1>
        <Button
          onClick={() => {
            setCreating(true);
          }}
        >
          Dodaj placówkę
        </Button>
      </div>
      {setActive.isError ? <ErrorMessage error={setActive.error} /> : null}
      <QueryState query={tenants} emptyTitle="Brak placówek" emptyHint="Dodaj pierwszą placówkę.">
        {(data) => (
          <DataTable
            caption="Placówki platformy"
            rows={data}
            rowKey={(t) => t.id}
            columns={[
              { header: 'Nazwa', cell: (t) => t.name },
              { header: 'Kod', cell: (t) => t.slug },
              {
                header: 'Status',
                cell: (t) =>
                  t.active ? (
                    <Badge variant="success">Aktywna</Badge>
                  ) : (
                    <Badge variant="secondary">Nieaktywna</Badge>
                  ),
              },
              {
                header: 'Akcje',
                cell: (t) => (
                  <div className="flex flex-wrap gap-2">
                    <Button
                      variant="outline"
                      aria-label={`Dodaj administratora placówki ${t.name}`}
                      onClick={() => {
                        setAdminFor(t);
                      }}
                    >
                      Dodaj administratora
                    </Button>
                    {t.active ? (
                      <Button
                        variant="outline"
                        aria-label={`Dezaktywuj placówkę ${t.name}`}
                        onClick={() => {
                          setActive.reset();
                          setDeactivating(t);
                        }}
                      >
                        Dezaktywuj
                      </Button>
                    ) : (
                      <Button
                        variant="outline"
                        aria-label={`Aktywuj placówkę ${t.name}`}
                        onClick={() => {
                          setActive.mutate(
                            { id: t.id, active: true },
                            {
                              onSuccess: () => {
                                notify.success('Placówka została aktywowana');
                              },
                            },
                          );
                        }}
                      >
                        Aktywuj
                      </Button>
                    )}
                  </div>
                ),
              },
            ]}
          />
        )}
      </QueryState>

      <Dialog
        open={creating}
        onClose={() => {
          setCreating(false);
        }}
        title="Nowa placówka"
      >
        {creating ? (
          <TenantForm
            onDone={() => {
              setCreating(false);
            }}
          />
        ) : null}
      </Dialog>
      <Dialog
        open={adminFor !== null}
        onClose={() => {
          setAdminFor(null);
        }}
        title="Administrator placówki"
        description={adminFor?.name ?? ''}
      >
        {adminFor ? (
          <TenantAdminForm
            tenantId={adminFor.id}
            onDone={() => {
              setAdminFor(null);
            }}
          />
        ) : null}
      </Dialog>
      <AlertDialog
        open={deactivating !== null}
        title="Dezaktywować placówkę?"
        description="Użytkownicy placówki nie będą mogli się zalogować."
        confirmLabel="Dezaktywuj placówkę"
        cancelLabel="Zostaw"
        destructive
        onCancel={() => {
          setDeactivating(null);
        }}
        onConfirm={() => {
          if (!deactivating) return;
          setActive.mutate(
            { id: deactivating.id, active: false },
            {
              onSuccess: () => {
                notify.success('Placówka została dezaktywowana');
              },
            },
          );
          setDeactivating(null);
        }}
      />
    </main>
  );
}
