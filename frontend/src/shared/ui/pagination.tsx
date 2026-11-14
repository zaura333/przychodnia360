import { Button } from '@/shared/ui/button';

interface PaginationProps {
  /** Numer strony liczony od 0. */
  page: number;
  size: number;
  total: number;
  onPage: (page: number) => void;
}

/** Stronicowanie z numerem strony i przyciskami poprzednia/następna. */
export function Pagination({ page, size, total, onPage }: PaginationProps) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1) return null;
  return (
    <nav aria-label="Stronicowanie" className="mt-3 flex items-center justify-center gap-3">
      <Button
        variant="outline"
        disabled={page <= 0}
        onClick={() => {
          onPage(page - 1);
        }}
      >
        Poprzednia
      </Button>
      <span className="text-sm" aria-live="polite">
        Strona {page + 1} z {pages}
      </span>
      <Button
        variant="outline"
        disabled={page + 1 >= pages}
        onClick={() => {
          onPage(page + 1);
        }}
      >
        Następna
      </Button>
    </nav>
  );
}
