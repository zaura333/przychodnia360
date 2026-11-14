import type { SessionUser } from '@/shared/api/authClient';
import type { SessionState } from '@/shared/api/session';
import type { Role } from '@/shared/lib/permissions';

/** Fabryki danych testowych (dane wyłącznie syntetyczne). */
export const testData = {
  user(role: Role = 'PATIENT', overrides: Partial<SessionUser> = {}): SessionUser {
    return {
      userId: `user-${role.toLowerCase()}`,
      role,
      tenantId: 'tenant-1',
      tenantName: 'Przychodnia Testowa',
      ...overrides,
    };
  },

  session(role: Role = 'PATIENT', overrides: Partial<SessionUser> = {}): SessionState {
    return { status: 'authenticated', user: testData.user(role, overrides) };
  },

  service(overrides: Record<string, unknown> = {}) {
    return {
      id: 's-1',
      name: 'Konsultacja',
      description: 'Opis usługi',
      categoryId: 'c-1',
      price: 150,
      durationMinutes: 30,
      performerType: 'DOCTOR',
      active: true,
      ...overrides,
    };
  },

  /** Problem Details w formacie backendu: type, title, status, detail, traceId. */
  problem(type: string, status: number, extra: Record<string, unknown> = {}) {
    return {
      type: `/problems/${type}`,
      title: 'Tytuł z serwera',
      status,
      detail: 'Szczegóły z serwera',
      traceId: 'trace-test-0001',
      ...extra,
    };
  },
};

/** Odpowiedź GET /me w kształcie backendu. */
export function meResponse(role: Role = 'PATIENT', overrides: Record<string, unknown> = {}) {
  return {
    userId: `user-${role.toLowerCase()}`,
    role,
    tenantId: role === 'PLATFORM_ADMIN' ? null : 'tenant-1',
    tenantName: role === 'PLATFORM_ADMIN' ? null : 'Klinika A',
    slotStepMinutes: 15,
    ...overrides,
  };
}

/** Problem Details jako odpowiedź MSW. */
export function problemResponse(
  type: string,
  status: number,
  extra: Record<string, unknown> = {},
): Response {
  return new Response(JSON.stringify(testData.problem(type, status, extra)), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });
}

/** Strona wyników w kształcie backendu (PageResponse). */
export function page<T>(items: T[], overrides: Record<string, unknown> = {}) {
  return { items, page: 0, size: 12, total: items.length, ...overrides };
}
