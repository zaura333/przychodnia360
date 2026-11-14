import { Link, NavLink, Navigate, Outlet, useLocation, useNavigate } from 'react-router';
import { StartupScreen, ForbiddenPage } from '@/app/pages/pages';
import { NAVIGATION } from '@/app/navigation';
import { areaForPath } from '@/app/areas';
import { useAuth } from '@/shared/api/session';
import { can, ROLE_LABELS } from '@/shared/lib/permissions';
import { Button } from '@/shared/ui/button';
import { DemoBanner } from '@/shared/ui/demo-banner';
import { ErrorBoundary } from '@/shared/ui/error-boundary';
import { SlowBackendNotice } from '@/shared/ui/slow-backend-notice';
import { Toaster } from '@/shared/ui/toaster';

/** Zawsze: baner demo (poza ErrorBoundary), powiadomienia i granica błędów aplikacji. */
export function RootLayout() {
  return (
    <>
      <DemoBanner />
      <SlowBackendNotice />
      <ErrorBoundary level="app">
        <Outlet />
      </ErrorBoundary>
      <Toaster />
    </>
  );
}

export function PublicLayout() {
  return (
    <div className="min-h-[calc(100vh-2.5rem)]">
      <Outlet />
    </div>
  );
}

/** Nagłówek z placówką i użytkownikiem, menu z navigation.ts i wylogowanie. */
export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  if (!user) return null;
  return (
    <div>
      <header className="flex flex-wrap items-center gap-4 border-b border-border px-4 py-2">
        <Link to="/" className="font-semibold">
          {user.tenantName ?? 'Przychodnia360'}
        </Link>
        <nav aria-label="Menu główne" className="flex flex-1 flex-wrap gap-2">
          {NAVIGATION[user.role].map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end
              className="touch-target inline-flex items-center rounded-md px-3 text-sm hover:bg-muted aria-[current=page]:bg-accent aria-[current=page]:text-accent-foreground"
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
        <span className="text-sm text-muted-foreground">{ROLE_LABELS[user.role]}</span>
        <Button
          variant="outline"
          onClick={() => {
            void logout().then(() => navigate('/logowanie', { replace: true }));
          }}
        >
          Wyloguj
        </Button>
      </header>
      <Outlet />
    </div>
  );
}

/** Granica błędów obszaru: awaria jednego obszaru nie wyłącza reszty aplikacji. */
export function AreaLayout() {
  return (
    <ErrorBoundary level="area">
      <Outlet />
    </ErrorBoundary>
  );
}

/**
 * Trasa chroniona. loading -> ekran startowy; anonymous -> /logowanie z zapisem docelowej ścieżki;
 * zła rola -> ForbiddenPage. To tylko wygoda UI: dostęp do danych egzekwuje backend.
 */
export function RequireRole() {
  const { status, user } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <StartupScreen />;
  if (!user) {
    return (
      <Navigate to="/logowanie" replace state={{ from: location.pathname + location.search }} />
    );
  }
  const area = areaForPath(location.pathname);
  if (area && !can(user, area.action)) return <ForbiddenPage />;
  return <Outlet />;
}
