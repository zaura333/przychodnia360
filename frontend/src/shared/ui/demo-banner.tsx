/**
 * NFR-07: stały, nieusuwalny pasek nad całą aplikacją (oba motywy). Wariant z P1 dochodzi w etapie 4.
 * Renderowany poza ErrorBoundary trasy, więc jest widoczny także w stanie błędu aplikacji.
 */
export function DemoBanner({ variant = 'default' }: { variant?: 'default' | 'p1' }) {
  return (
    <div
      role="region"
      aria-label="Informacja o wersji demonstracyjnej"
      className="bg-warning px-4 py-2 text-center text-sm font-medium text-warning-foreground"
    >
      Wersja demonstracyjna. Dane wyłącznie syntetyczne. Nie wprowadzaj prawdziwych danych osobowych
      ani medycznych.
      {variant === 'p1' ? ' Usługi platformy P1 są symulowane.' : null}
    </div>
  );
}
