import type { ReactNode } from 'react';
import { describeError } from '@/shared/lib/errors';
import { Button } from '@/shared/ui/button';
import { Skeleton } from '@/shared/ui/display';

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="rounded-md border border-dashed border-border p-8 text-center">
      <p className="font-medium">{title}</p>
      {hint ? <p className="mt-1 text-sm text-muted-foreground">{hint}</p> : null}
    </div>
  );
}

export function ErrorMessage({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { title, message, requestId } = describeError(error);
  return (
    <div role="alert" className="rounded-md border border-destructive p-4">
      <p className="font-medium text-destructive">{title}</p>
      <p className="mt-1 text-sm">{message}</p>
      {requestId ? (
        <p className="mt-1 text-xs text-muted-foreground">Identyfikator: {requestId}</p>
      ) : null}
      {onRetry ? (
        <Button className="mt-3" variant="outline" onClick={onRetry}>
          Spróbuj ponownie
        </Button>
      ) : null}
    </div>
  );
}

/** Minimalny kształt wyniku zapytania (zgodny z UseQueryResult z TanStack Query). */
interface QueryLike<T> {
  isPending: boolean;
  isError: boolean;
  error: unknown;
  data: T | undefined;
  refetch: () => unknown;
}

interface QueryStateProps<T> {
  query: QueryLike<T>;
  /** Czy dane są puste (domyślnie: pusta tablica). */
  isEmpty?: (data: T) => boolean;
  emptyTitle?: string;
  emptyHint?: string;
  /** Dla list: liczba wyników wyświetlana nad zawartością. */
  count?: (data: T) => number;
  children: (data: T) => ReactNode;
}

/**
 * Jedyny sposób wyświetlania wyniku zapytania na ekranach: szkielet, stan pusty, błąd z ponowieniem
 * albo dzieci. Ekrany nie piszą własnych warunków ładowania i błędów.
 */
export function QueryState<T>({
  query,
  isEmpty = (data) => Array.isArray(data) && data.length === 0,
  emptyTitle = 'Brak danych',
  emptyHint,
  count,
  children,
}: QueryStateProps<T>) {
  if (query.isPending) {
    return (
      <div role="status" aria-busy="true" aria-label="Ładowanie" className="flex flex-col gap-2">
        <Skeleton className="h-6 w-1/3" />
        <Skeleton className="h-24 w-full" />
      </div>
    );
  }
  if (query.isError) {
    return (
      <ErrorMessage
        error={query.error}
        onRetry={() => {
          void query.refetch();
        }}
      />
    );
  }
  const data = query.data as T;
  if (isEmpty(data)) {
    return emptyHint ? (
      <EmptyState title={emptyTitle} hint={emptyHint} />
    ) : (
      <EmptyState title={emptyTitle} />
    );
  }
  return (
    <>
      {count ? (
        <p className="mb-2 text-sm text-muted-foreground">Liczba wyników: {count(data)}</p>
      ) : null}
      {children(data)}
    </>
  );
}
