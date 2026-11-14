import type { ReactNode } from 'react';
import { canVisit } from '@/app/routeTable';
import { ForbiddenPage } from '@/app/pages/pages';
import { useAuth } from '@/shared/api/session';

/** Strażnik trasy: rola spoza `ROUTE_TABLE` widzi stronę 403 zamiast ekranu. */
export function RoleGate({ path, children }: { path: string; children: ReactNode }) {
  const { user } = useAuth();
  if (!user || !canVisit(user.role, path)) return <ForbiddenPage />;
  return <>{children}</>;
}
