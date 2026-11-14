/**
 * Uprawnienia po stronie interfejsu to WYŁĄCZNIE wygoda (ukrywanie pozycji menu i przycisków).
 * Prawdziwa kontrola dostępu jest po stronie backendu; nigdy nie polegaj na tym pliku dla bezpieczeństwa.
 */
export const ROLES = [
  'PATIENT',
  'DOCTOR',
  'MEDICAL_STAFF',
  'RECEPTIONIST',
  'TENANT_ADMIN',
  'PLATFORM_ADMIN',
] as const;
export type Role = (typeof ROLES)[number];

export const ROLE_LABELS: Record<Role, string> = {
  PATIENT: 'Pacjent',
  DOCTOR: 'Lekarz',
  MEDICAL_STAFF: 'Personel medyczny',
  RECEPTIONIST: 'Rejestracja',
  TENANT_ADMIN: 'Administrator placówki',
  PLATFORM_ADMIN: 'Administrator platformy',
};

/** W etapie 0 akcje to wejścia do obszarów; kolejne dochodzą razem z funkcjami. */
export type Action = 'area:patient' | 'area:staff' | 'area:admin' | 'area:platform';

export const ROLE_ACTIONS: Record<Role, readonly Action[]> = {
  PATIENT: ['area:patient'],
  DOCTOR: ['area:staff'],
  MEDICAL_STAFF: ['area:staff'],
  RECEPTIONIST: ['area:staff'],
  TENANT_ADMIN: ['area:admin'],
  PLATFORM_ADMIN: ['area:platform'],
};

export interface PermissionSubject {
  role: Role;
  /** Dodatkowe uprawnienia nadane indywidualnie (FR-37, 1c). */
  extraActions?: readonly Action[] | undefined;
}

export function can(subject: PermissionSubject | null | undefined, action: Action): boolean {
  if (!subject) return false;
  return (
    ROLE_ACTIONS[subject.role].includes(action) || (subject.extraActions?.includes(action) ?? false)
  );
}

export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}
