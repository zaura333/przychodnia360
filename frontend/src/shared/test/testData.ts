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
      displayName: 'Jan Testowy',
      tenantName: 'Przychodnia Testowa',
      ...overrides,
    };
  },

  session(role: Role = 'PATIENT', overrides: Partial<SessionUser> = {}): SessionState {
    return { status: 'authenticated', user: testData.user(role, overrides) };
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
