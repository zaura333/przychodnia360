import { ApiError } from './apiError';
import { refresh } from './authClient';
import { sessionExpired } from './events';
import { performFetch, type CoreOptions } from './fetchCore';
import { tokenStore } from './tokenStore';

/** Żądań do /auth/* nie odświeżamy ani nie ponawiamy (ochrona przed pętlą). */
const isAuthUrl = (url: string): boolean => /\/auth\//.test(url);

/** Jedno wspólne odświeżenie dla wszystkich równoległych 401 (single-flight). */
let inflightRefresh: Promise<string | null> | null = null;

function refreshOnce(): Promise<string | null> {
  inflightRefresh ??= refresh()
    .then((token) => {
      tokenStore.set(token);
      return token;
    })
    .catch(() => null)
    .finally(() => {
      inflightRefresh = null;
    });
  return inflightRefresh;
}

function withToken(options: CoreOptions, token: string | null): CoreOptions {
  if (!token) return options;
  const headers = new Headers(options.headers);
  headers.set('Authorization', `Bearer ${token}`);
  return { ...options, headers };
}

/**
 * Mutator Orval (httpClient: fetch). Zwraca { data, status, headers } zgodnie z generowanym klientem.
 * Przy 401: jedno odświeżenie, jednorazowe ponowienie; po porażce zdarzenie sessionExpired i odrzucenie.
 */
export async function customFetch<T>(url: string, options: CoreOptions = {}): Promise<T> {
  const attempt = (token: string | null) => performFetch<unknown>(url, withToken(options, token));

  try {
    return (await attempt(tokenStore.get())) as T;
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401 || isAuthUrl(url)) throw error;

    const token = await refreshOnce();
    if (!token) {
      tokenStore.clear();
      sessionExpired.emit();
      throw error;
    }
    return (await attempt(token)) as T;
  }
}

export type { CoreOptions };
