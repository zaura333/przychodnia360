import { Link, useParams } from 'react-router';
import { useCategories, useService } from '../hooks';
import { PERFORMER_LABELS } from '../labels';
import { NotFoundPage } from '@/app/pages/pages';
import { ApiError } from '@/shared/api/apiError';
import { formatDuration, formatPrice } from '@/shared/lib/format';
import { Button } from '@/shared/ui/button';
import { QueryState } from '@/shared/ui/query-state';

/** Szczegóły usługi (FR-22). Usługa z innej placówki lub nieistniejąca to strona 404. */
export function ServiceDetailsPage() {
  const { id } = useParams();
  const service = useService(id);
  const categories = useCategories();
  if (service.error instanceof ApiError && service.error.status === 404) return <NotFoundPage />;
  const names = new Map((categories.data ?? []).map((c) => [c.id, c.name]));
  return (
    <main className="mx-auto max-w-2xl p-4">
      <QueryState query={service} isEmpty={() => false}>
        {(s) => (
          <article className="flex flex-col gap-3">
            <h1 className="text-2xl font-semibold">{s.name}</h1>
            <p className="text-sm text-muted-foreground">{names.get(s.categoryId) ?? ''}</p>
            {s.description ? <p>{s.description}</p> : null}
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
              <dt className="text-muted-foreground">Cena</dt>
              <dd>{formatPrice(s.price)}</dd>
              <dt className="text-muted-foreground">Czas trwania</dt>
              <dd>{formatDuration(s.durationMinutes)}</dd>
              <dt className="text-muted-foreground">Wykonawca</dt>
              <dd>{PERFORMER_LABELS[s.performerType]}</dd>
            </dl>
            <div className="flex gap-2">
              <Button asChild>
                <Link to={`/pacjent/rezerwacja?serviceId=${s.id}`}>Umów wizytę</Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/pacjent/uslugi">Wróć do listy</Link>
              </Button>
            </div>
          </article>
        )}
      </QueryState>
    </main>
  );
}
