import { useSearchParams } from 'react-router';
import { ServiceCard } from '../components/ServiceCard';
import { ServiceFilters, type ServiceFilterValues } from '../components/ServiceFilters';
import { useCategories, useServices } from '../hooks';
import type { ListServicesParams } from '@/shared/api/generated/model';
import { useAuth } from '@/shared/api/session';
import { Pagination } from '@/shared/ui/pagination';
import { QueryState } from '@/shared/ui/query-state';

const PAGE_SIZE = 12;

const toNumber = (v: string): number | undefined => {
  if (v === '') return undefined;
  const n = Number(v);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
};

/** Lista usług z filtrami w adresie (FR-02). Pacjent widzi "Umów", pracownicy tylko odczyt. */
export function ServicesPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const categories = useCategories();

  const filters: ServiceFilterValues = {
    categoryId: params.get('categoryId') ?? '',
    minPrice: params.get('minPrice') ?? '',
    maxPrice: params.get('maxPrice') ?? '',
    performerType: params.get('performerType') ?? '',
  };
  const page = Math.max(0, Number(params.get('page') ?? '0') || 0);

  const query: ListServicesParams = { page, size: PAGE_SIZE };
  if (filters.categoryId) query.categoryId = filters.categoryId;
  const min = toNumber(filters.minPrice);
  const max = toNumber(filters.maxPrice);
  if (min !== undefined) query.minPrice = min;
  if (max !== undefined) query.maxPrice = max;
  if (filters.performerType === 'DOCTOR' || filters.performerType === 'MEDICAL_STAFF') {
    query.performerType = filters.performerType;
  }
  const services = useServices(query);

  const isPatient = user?.role === 'PATIENT';
  const names = new Map((categories.data ?? []).map((c) => [c.id, c.name]));

  const apply = (next: ServiceFilterValues) => {
    const p = new URLSearchParams();
    for (const [k, v] of Object.entries(next) as [string, string][]) if (v !== '') p.set(k, v);
    setParams(p);
  };

  return (
    <main className="mx-auto max-w-5xl p-4">
      <h1 className="mb-4 text-2xl font-semibold">Usługi</h1>
      <ServiceFilters
        key={params.toString()}
        categories={categories.data ?? []}
        value={filters}
        onApply={apply}
      />
      <QueryState
        query={services}
        isEmpty={(d) => d.items.length === 0}
        emptyTitle="Brak usług spełniających kryteria"
        emptyHint="Zmień lub wyczyść filtry."
      >
        {(data) => (
          <>
            <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {data.items.map((s) => (
                <li key={s.id}>
                  <ServiceCard
                    service={s}
                    categoryName={names.get(s.categoryId)}
                    {...(isPatient
                      ? {
                          detailsTo: `/pacjent/uslugi/${s.id}`,
                          bookTo: `/pacjent/rezerwacja?serviceId=${s.id}`,
                        }
                      : {})}
                  />
                </li>
              ))}
            </ul>
            <Pagination
              page={data.page}
              size={data.size}
              total={data.total}
              onPage={(next) => {
                const p = new URLSearchParams(params);
                p.set('page', String(next));
                setParams(p);
              }}
            />
          </>
        )}
      </QueryState>
    </main>
  );
}
