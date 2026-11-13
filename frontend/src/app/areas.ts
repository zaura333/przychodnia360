import type { Action, Role } from '@/shared/lib/permissions';

/** Obszary aplikacji (osobne chunki). Tabela napędza routing i testy "rola x trasa x wynik". */
export const AREAS = [
  { path: '/pacjent', action: 'area:patient' },
  { path: '/pracownik', action: 'area:staff' },
  { path: '/administracja', action: 'area:admin' },
  { path: '/platforma', action: 'area:platform' },
] as const satisfies readonly { path: string; action: Action }[];

export type AreaPath = (typeof AREAS)[number]['path'];

const DEFAULT_AREA: Record<Role, AreaPath> = {
  PATIENT: '/pacjent',
  DOCTOR: '/pracownik',
  MEDICAL_STAFF: '/pracownik',
  RECEPTIONIST: '/pracownik',
  TENANT_ADMIN: '/administracja',
  PLATFORM_ADMIN: '/platforma',
};

/** Obszar, do którego trafia użytkownik po zalogowaniu. */
export function defaultAreaFor(role: Role): AreaPath {
  return DEFAULT_AREA[role];
}

export function areaForPath(path: string) {
  return AREAS.find((a) => path === a.path || path.startsWith(`${a.path}/`));
}
