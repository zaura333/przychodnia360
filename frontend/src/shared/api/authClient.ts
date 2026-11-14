import { ApiError } from './apiError';
import { performFetch } from './fetchCore';
import { tokenStore } from './tokenStore';
import { isRole, type Role } from '@/shared/lib/permissions';

/**
 * Ręczne wywołania /auth/* i /me (wyjątek od generowanego klienta: mutator sam z nich korzysta, więc
 * wygenerowany klient dawałby zależność cykliczną). Typy tymczasowe zgodne z planem backendu 1a;
 * w 1a zastępowane typami z klienta generowanego (patrz ADR-F03).
 */
export interface SessionUser {
  userId: string;
  role: Role;
  tenantId: string | null;
  tenantName?: string | undefined;
  employeeId?: string | undefined;
  patientId?: string | undefined;
  /** Krok siatki wizyt w minutach (z konfiguracji placówki); brak oznacza wartość domyślną. */
  slotStepMinutes?: number | undefined;
}

export interface TokenResponse {
  accessToken: string;
}

/** Odpowiedź logowania: sam token, profil pobiera AuthProvider z GET /me. */
export interface LoginResponse extends TokenResponse {
  user?: SessionUser;
}

/** Odświeża token dostępu na podstawie ciasteczka HttpOnly (bez ciała żądania). */
export async function refresh(): Promise<string> {
  const { data } = await performFetch<TokenResponse>('/api/v1/auth/refresh', { method: 'POST' });
  if (typeof data.accessToken !== 'string' || data.accessToken === '') {
    throw new ApiError({ title: 'Niepoprawna odpowiedź odświeżania sesji', status: 502 });
  }
  return data.accessToken;
}

export async function logout(): Promise<void> {
  try {
    await performFetch<undefined>('/api/v1/auth/logout', { method: 'POST' });
  } finally {
    tokenStore.clear();
  }
}

export async function fetchMe(): Promise<SessionUser> {
  const token = tokenStore.get();
  const { data } = await performFetch<Record<string, unknown>>('/api/v1/me', {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const { userId, role, tenantId, tenantName, employeeId, patientId, slotStepMinutes } = data;
  if (typeof userId !== 'string' || !isRole(role)) {
    throw new ApiError({ title: 'Niepoprawna odpowiedź /me', status: 502 });
  }
  return {
    userId,
    role,
    tenantId: typeof tenantId === 'string' ? tenantId : null,
    tenantName: typeof tenantName === 'string' ? tenantName : undefined,
    employeeId: typeof employeeId === 'string' ? employeeId : undefined,
    patientId: typeof patientId === 'string' ? patientId : undefined,
    slotStepMinutes: typeof slotStepMinutes === 'number' ? slotStepMinutes : undefined,
  };
}
