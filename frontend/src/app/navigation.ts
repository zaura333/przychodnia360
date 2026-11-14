import type { Role } from '@/shared/lib/permissions';

export interface NavItem {
  label: string;
  to: string;
}

/**
 * Deklaratywna tabela rola -> pozycje menu. Ta sama tabela napędza test "rola x pozycja menu":
 * każda pozycja musi prowadzić do trasy dozwolonej dla tej roli w ROUTE_TABLE.
 */
export const NAVIGATION: Record<Role, readonly NavItem[]> = {
  PATIENT: [
    { label: 'Moje wizyty', to: '/pacjent/wizyty' },
    { label: 'Umów wizytę', to: '/pacjent/rezerwacja' },
    { label: 'Usługi', to: '/pacjent/uslugi' },
    { label: 'Lekarze', to: '/pacjent/lekarze' },
    { label: 'Moje dane', to: '/pacjent/moje-dane' },
  ],
  DOCTOR: [
    { label: 'Dzisiaj', to: '/pracownik' },
    { label: 'Mój grafik', to: '/pracownik/grafik' },
    { label: 'Usługi', to: '/pracownik/uslugi' },
  ],
  MEDICAL_STAFF: [{ label: 'Pulpit', to: '/pracownik' }],
  RECEPTIONIST: [
    { label: 'Terminarz', to: '/pracownik/terminarz' },
    { label: 'Pacjenci', to: '/pracownik/pacjenci' },
    { label: 'Usługi', to: '/pracownik/uslugi' },
  ],
  TENANT_ADMIN: [
    { label: 'Usługi', to: '/administracja/uslugi' },
    { label: 'Pracownicy', to: '/administracja/pracownicy' },
    { label: 'Wizyty', to: '/administracja/wizyty' },
  ],
  PLATFORM_ADMIN: [{ label: 'Placówki', to: '/platforma/placowki' }],
};
