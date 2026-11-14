import type { Role } from '@/shared/lib/permissions';

export interface RouteSpec {
  /** Wzorzec trasy (jak w routerze). */
  path: string;
  /** Przykładowy adres z wypełnionymi parametrami (do testów). */
  example: string;
  /** Role, które mogą wejść na trasę. Pozostałe widzą stronę 403. */
  roles: readonly Role[];
}

/**
 * Jedyne źródło prawdy o dostępie do tras wewnątrz obszarów (plan 1a, sekcja 5). Z tej tabeli korzysta
 * `RoleGate` w obszarach oraz testy "rola x trasa x wynik" i "rola x pozycja menu". To tylko wygoda UI:
 * dostęp do danych egzekwuje backend.
 */
export const ROUTE_TABLE: readonly RouteSpec[] = [
  { path: '/pacjent/wizyty', example: '/pacjent/wizyty', roles: ['PATIENT'] },
  { path: '/pacjent/rezerwacja', example: '/pacjent/rezerwacja', roles: ['PATIENT'] },
  { path: '/pacjent/uslugi', example: '/pacjent/uslugi', roles: ['PATIENT'] },
  { path: '/pacjent/uslugi/:id', example: '/pacjent/uslugi/s-1', roles: ['PATIENT'] },
  { path: '/pacjent/lekarze', example: '/pacjent/lekarze', roles: ['PATIENT'] },
  { path: '/pacjent/moje-dane', example: '/pacjent/moje-dane', roles: ['PATIENT'] },
  {
    path: '/pracownik',
    example: '/pracownik',
    roles: ['DOCTOR', 'MEDICAL_STAFF', 'RECEPTIONIST'],
  },
  { path: '/pracownik/grafik', example: '/pracownik/grafik', roles: ['DOCTOR'] },
  { path: '/pracownik/terminarz', example: '/pracownik/terminarz', roles: ['RECEPTIONIST'] },
  { path: '/pracownik/pacjenci', example: '/pracownik/pacjenci', roles: ['RECEPTIONIST'] },
  { path: '/pracownik/uslugi', example: '/pracownik/uslugi', roles: ['DOCTOR', 'RECEPTIONIST'] },
  { path: '/administracja/uslugi', example: '/administracja/uslugi', roles: ['TENANT_ADMIN'] },
  {
    path: '/administracja/pracownicy',
    example: '/administracja/pracownicy',
    roles: ['TENANT_ADMIN'],
  },
  {
    path: '/administracja/pracownicy/:id/grafik',
    example: '/administracja/pracownicy/e-1/grafik',
    roles: ['TENANT_ADMIN'],
  },
  { path: '/administracja/wizyty', example: '/administracja/wizyty', roles: ['TENANT_ADMIN'] },
  { path: '/platforma/placowki', example: '/platforma/placowki', roles: ['PLATFORM_ADMIN'] },
];

export function routeSpec(path: string): RouteSpec {
  const spec = ROUTE_TABLE.find((r) => r.path === path);
  if (!spec) throw new Error(`Trasa ${path} nie jest zapisana w ROUTE_TABLE`);
  return spec;
}

export function canVisit(role: Role, path: string): boolean {
  return routeSpec(path).roles.includes(role);
}
