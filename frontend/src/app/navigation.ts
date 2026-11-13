import type { Role } from '@/shared/lib/permissions';

export interface NavItem {
  label: string;
  to: string;
}

/**
 * Deklaratywna tabela rola -> pozycje menu. W etapie 0 wszystkie pozycje prowadzą do SoonPage.
 * Ta sama tabela napędza test "rola x pozycja menu" (menu musi być spójne z uprawnieniami).
 */
export const NAVIGATION: Record<Role, readonly NavItem[]> = {
  PATIENT: [
    { label: 'Panel', to: '/pacjent' },
    { label: 'Moje wizyty', to: '/pacjent/wizyty' },
  ],
  DOCTOR: [
    { label: 'Pulpit', to: '/pracownik' },
    { label: 'Mój grafik', to: '/pracownik/grafik' },
    { label: 'Pacjenci', to: '/pracownik/pacjenci' },
  ],
  MEDICAL_STAFF: [
    { label: 'Pulpit', to: '/pracownik' },
    { label: 'Pacjenci', to: '/pracownik/pacjenci' },
  ],
  RECEPTIONIST: [
    { label: 'Pulpit', to: '/pracownik' },
    { label: 'Terminarz', to: '/pracownik/terminarz' },
    { label: 'Pacjenci', to: '/pracownik/pacjenci' },
  ],
  TENANT_ADMIN: [
    { label: 'Pulpit', to: '/administracja' },
    { label: 'Personel', to: '/administracja/personel' },
    { label: 'Usługi', to: '/administracja/uslugi' },
  ],
  PLATFORM_ADMIN: [
    { label: 'Pulpit', to: '/platforma' },
    { label: 'Placówki', to: '/platforma/placowki' },
  ],
};

/** Podścieżki (względem obszaru) występujące w menu dowolnej roli danego obszaru. */
export function subpathsFor(areaPath: string): string[] {
  const subs = new Set<string>();
  for (const items of Object.values(NAVIGATION)) {
    for (const item of items) {
      if (item.to.startsWith(`${areaPath}/`)) subs.add(item.to.slice(areaPath.length + 1));
    }
  }
  return [...subs];
}
