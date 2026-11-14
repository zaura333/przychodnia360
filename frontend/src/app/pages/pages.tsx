import { isRouteErrorResponse, Link, useRouteError } from 'react-router';
import { useAuth } from '@/shared/api/session';
import { Button } from '@/shared/ui/button';
import { Skeleton } from '@/shared/ui/display';

function Page({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <main className="mx-auto max-w-2xl p-8">
      <h1 className="text-2xl font-semibold">{title}</h1>
      {children ? <div className="mt-3 text-sm text-muted-foreground">{children}</div> : null}
    </main>
  );
}

export function SoonPage({ title = 'Wkrótce' }: { title?: string }) {
  return <Page title={title}>Ten ekran powstaje w kolejnych etapach.</Page>;
}

export function NotFoundPage() {
  return (
    <Page title="Nie znaleziono strony">
      <p>Strona, której szukasz, nie istnieje.</p>
      <Button asChild className="mt-4">
        <Link to="/">Wróć na stronę główną</Link>
      </Button>
    </Page>
  );
}

export function ForbiddenPage() {
  return (
    <Page title="Brak dostępu">
      <p>Nie masz uprawnień do tego obszaru.</p>
      <Button asChild className="mt-4" variant="outline">
        <Link to="/">Wróć na stronę główną</Link>
      </Button>
    </Page>
  );
}

/** Nieoczekiwany błąd renderowania trasy: identyfikator do zgłoszenia, bez treści błędu i stosu. */
export function ErrorPage() {
  const error = useRouteError();
  const errorId = crypto.randomUUID().slice(0, 8);
  if (isRouteErrorResponse(error) && error.status === 404) return <NotFoundPage />;
  return (
    <Page title="Coś poszło nie tak">
      <div role="alert">
        <p>Wystąpił nieoczekiwany błąd. Podaj identyfikator, jeśli zgłaszasz problem.</p>
        <p className="mt-2">Identyfikator: {errorId}</p>
      </div>
      <Button asChild className="mt-4">
        <a href="/">Wróć do strony głównej</a>
      </Button>
    </Page>
  );
}

/** Ekran startowy podczas ciszego odświeżania sesji (bez migania ekranu logowania). */
export function StartupScreen() {
  return (
    <div
      role="status"
      aria-busy="true"
      aria-label="Ładowanie aplikacji"
      className="mx-auto max-w-2xl p-8"
    >
      <Skeleton className="h-8 w-1/2" />
      <Skeleton className="mt-4 h-32 w-full" />
    </div>
  );
}

/** Szkielet ekranu logowania (formularz dochodzi w 1a); pokazuje komunikat, gdy backend jest niedostępny. */
export function LoginPage() {
  const { backendUnavailable } = useAuth();
  return (
    <Page title="Logowanie">
      {backendUnavailable ? (
        <p role="alert" className="mb-3 rounded-md border border-destructive p-3 text-destructive">
          Nie można połączyć z serwerem. Spróbuj ponownie za chwilę.
        </p>
      ) : null}
      <p>Formularz logowania powstaje w kolejnych etapach.</p>
    </Page>
  );
}
