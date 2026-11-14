import { QueryClient } from '@tanstack/react-query';
import { ApiError } from './apiError';

const MAX_RETRIES = 2;

/** Ponawiamy tylko błędy sieci i 5xx (maks. 2 razy), nigdy 4xx. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES) return false;
  if (!(error instanceof ApiError)) return false;
  return error.isNetwork || error.isServerError();
}

export const retryDelay = (attempt: number): number => Math.min(1000 * 2 ** attempt, 8000);

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: shouldRetry,
        retryDelay,
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        gcTime: 5 * 60_000, // dane medyczne (1b) nadpisują na 60 s
      },
      // Brak ponowień zapisów (zasada z 1b)
      mutations: { retry: 0 },
    },
  });
}

/**
 * Klucze zapytań zależne od zalogowanego użytkownika (podwójne zabezpieczenie przed wyciekiem
 * danych między użytkownikami obok czyszczenia cache przy wylogowaniu).
 */
export const qk = {
  user: (userId: string) => ({
    all: ['user', userId] as const,
    scoped: (...key: readonly unknown[]) => ['user', userId, ...key] as const,
  }),
};
