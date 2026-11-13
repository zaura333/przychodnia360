import type { ReactNode } from 'react';
import { can, type Action } from './index';
import { useAuth } from '@/shared/api/session';

/** Renderuje dzieci tylko wtedy, gdy zalogowany użytkownik ma daną akcję (wyłącznie wygoda UI). */
export function Can({ action, children }: { action: Action; children: ReactNode }) {
  const { user } = useAuth();
  return can(user, action) ? <>{children}</> : null;
}
