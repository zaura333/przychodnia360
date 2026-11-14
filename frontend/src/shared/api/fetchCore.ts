import { ApiError } from './apiError';
import { slowRequests } from './events';
import { env } from '@/shared/config/env';

export const DEFAULT_TIMEOUT_MS = 30_000;
export const SLOW_AFTER_MS = 5_000;

export interface CoreOptions extends RequestInit {
  timeoutMs?: number;
}

export interface CoreResponse<T> {
  data: T;
  status: number;
  headers: Headers;
}

/** Adresy z OpenAPI zaczynają się od /api/v1; prefiks podmieniamy na VITE_API_BASE (np. inny host). */
export function resolveUrl(url: string): string {
  if (/^https?:\/\//.test(url)) return url;
  if (url === '/api/v1' || url.startsWith('/api/v1/')) {
    return env.apiBase + url.slice('/api/v1'.length);
  }
  return url.startsWith('/') ? env.apiBase + url : url;
}

function newRequestId(): string {
  return crypto.randomUUID();
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (text === '') return undefined;
  const type = response.headers.get('Content-Type') ?? '';
  if (type.includes('json')) {
    try {
      return JSON.parse(text) as unknown;
    } catch {
      return undefined;
    }
  }
  return text;
}

/**
 * Rdzeń fetch bez logiki sesji (używany też przez authClient, żeby uniknąć zależności cyklicznej):
 * identyfikator żądania, limit czasu rozróżniony od anulowania, zdarzenie powolnej odpowiedzi,
 * Problem Details -> ApiError, odpowiedzi 204 i puste ciała.
 */
export async function performFetch<T>(
  url: string,
  options: CoreOptions = {},
): Promise<CoreResponse<T>> {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, signal: callerSignal, headers, ...init } = options;
  const requestId = newRequestId();
  const merged = new Headers(headers);
  merged.set('X-Request-Id', requestId);
  if (!merged.has('Accept')) merged.set('Accept', 'application/json');

  const controller = new AbortController();
  const state = { timedOut: false, slow: false };
  const onCallerAbort = () => {
    controller.abort();
  };
  if (callerSignal?.aborted) controller.abort();
  callerSignal?.addEventListener('abort', onCallerAbort, { once: true });

  const timer = setTimeout(() => {
    state.timedOut = true;
    controller.abort();
  }, timeoutMs);
  const slowTimer = setTimeout(() => {
    state.slow = true;
    slowRequests.increment();
  }, SLOW_AFTER_MS);

  try {
    const response = await fetch(resolveUrl(url), {
      credentials: 'same-origin',
      ...init,
      headers: merged,
      signal: controller.signal,
    });
    const body = await readBody(response);
    if (!response.ok) {
      throw ApiError.fromResponse(response, body);
    }
    return { data: body as T, status: response.status, headers: response.headers };
  } catch (error) {
    if (error instanceof ApiError) throw error;
    if (state.timedOut) throw ApiError.timeout(requestId);
    if (callerSignal?.aborted) throw ApiError.aborted(requestId);
    throw ApiError.network(requestId);
  } finally {
    clearTimeout(timer);
    clearTimeout(slowTimer);
    if (state.slow) slowRequests.decrement();
    callerSignal?.removeEventListener('abort', onCallerAbort);
  }
}
