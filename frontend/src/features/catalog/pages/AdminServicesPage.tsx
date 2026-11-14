import { useState } from 'react';
import { CategoryManager } from '../components/CategoryManager';
import { ServiceForm } from '../components/ServiceForm';
import { useCategories, useDeactivateService, useServices } from '../hooks';
import { PERFORMER_LABELS } from '../labels';
import type { ServiceResponse } from '@/shared/api/generated/model';
import { formatDuration, formatPrice } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { DataTable } from '@/shared/ui/data-table';
import { AlertDialog, Dialog } from '@/shared/ui/dialog';
import { Pagination } from '@/shared/ui/pagination';
import { ErrorMessage, QueryState } from '@/shared/ui/query-state';
import { useNotify } from '@/shared/ui/toaster';

const PAGE_SIZE = 20;

/**
 * Zarządzanie usługami (FR-33) i kategoriami (FR-57). Lista pokazuje usługi aktywne: backend nie udostępnia
 * jeszcze listy usług nieaktywnych (zapis w dzienniku błędów).
 */
export function AdminServicesPage() {
  const [page, setPage] = useState(0);
  const services = useServices({ page, size: PAGE_SIZE });
  const categories = useCategories();
  const deactivate = useDeactivateService();
  const notify = useNotify();
  const [editing, setEditing] = useState<ServiceResponse | 'new' | null>(null);
  const [removing, setRemoving] = useState<ServiceResponse | null>(null);
  const names = new Map((categories.data ?? []).map((c) => [c.id, c.name]));

  return (
    <main className="mx-auto max-w-5xl p-4">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Usługi</h1>
        <Button
          disabled={(categories.data ?? []).length === 0}
          onClick={() => {
            setEditing('new');
          }}
        >
          Dodaj usługę
        </Button>
      </div>
      {deactivate.isError ? <ErrorMessage error={deactivate.error} /> : null}
      <QueryState
        query={services}
        isEmpty={(d) => d.items.length === 0}
        emptyTitle="Brak usług"
        emptyHint="Dodaj pierwszą usługę, żeby pacjenci mogli umawiać wizyty."
      >
        {(data) => (
          <>
            <DataTable
              caption="Usługi placówki"
              rows={data.items}
              rowKey={(s) => s.id}
              columns={[
                { header: 'Nazwa', cell: (s) => s.name },
                { header: 'Kategoria', cell: (s) => names.get(s.categoryId) ?? '' },
                { header: 'Cena', cell: (s) => formatPrice(s.price) },
                { header: 'Czas', cell: (s) => formatDuration(s.durationMinutes) },
                { header: 'Wykonawca', cell: (s) => PERFORMER_LABELS[s.performerType] },
                {
                  header: 'Akcje',
                  cell: (s) => (
                    <div className="flex gap-2">
                      <Button
                        variant="outline"
                        aria-label={`Edytuj usługę ${s.name}`}
                        onClick={() => {
                          setEditing(s);
                        }}
                      >
                        Edytuj
                      </Button>
                      <Button
                        variant="outline"
                        aria-label={`Dezaktywuj usługę ${s.name}`}
                        onClick={() => {
                          deactivate.reset();
                          setRemoving(s);
                        }}
                      >
                        Dezaktywuj
                      </Button>
                    </div>
                  ),
                },
              ]}
            />
            <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />
          </>
        )}
      </QueryState>

      <CategoryManager />

      <Dialog
        open={editing !== null}
        onClose={() => {
          setEditing(null);
        }}
        title={editing === 'new' ? 'Nowa usługa' : 'Edycja usługi'}
      >
        {editing !== null ? (
          <ServiceForm
            service={editing === 'new' ? undefined : editing}
            categories={categories.data ?? []}
            onDone={() => {
              setEditing(null);
            }}
          />
        ) : null}
      </Dialog>

      <AlertDialog
        open={removing !== null}
        title="Dezaktywować usługę?"
        description="Usługa zniknie z oferty, wcześniejsze wizyty pozostaną w historii."
        confirmLabel="Dezaktywuj usługę"
        cancelLabel="Zostaw"
        destructive
        onCancel={() => {
          setRemoving(null);
        }}
        onConfirm={() => {
          if (!removing) return;
          deactivate.mutate(removing.id, {
            onSuccess: () => {
              notify.success('Usługa została dezaktywowana');
            },
          });
          setRemoving(null);
        }}
      />
    </main>
  );
}
