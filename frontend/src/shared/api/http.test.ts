import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from './apiError';
import { sessionExpired, slowRequests } from './events';
import { SLOW_AFTER_MS } from './fetchCore';
import { customFetch } from './http';
import { tokenStore } from './tokenStore';

type Handler = (url: string, init: RequestInit) => Response | Promise<Response>;

function stubFetch(handler: Handler) {
  const fn = vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
    Promise.resolve(
      handler(
        typeof input === 'string' ? input : input instanceof URL ? input.href : input.url,
        init ?? {},
      ),
    ),
  );
  vi.stubGlobal('fetch', fn);
  return fn;
}

const json = (body: unknown, status = 200, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });

const problem = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  });

const auth = (init: RequestInit) => new Headers(init.headers).get('Authorization');

beforeEach(() => {
  tokenStore.clear();
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('customFetch: żądanie', () => {
  it('attaches bearer token, request id and returns {data,status,headers}', async () => {
    tokenStore.set('tok-1');
    const fetchMock = stubFetch(() => json({ status: 'UP' }));

    const result = await customFetch<{ data: { status: string }; status: number }>(
      '/api/v1/health',
    );

    expect(result.data).toEqual({ status: 'UP' });
    expect(result.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/health');
    expect(auth(init)).toBe('Bearer tok-1');
    expect(new Headers(init.headers).get('X-Request-Id')).toMatch(/^[0-9a-f-]{36}$/);
    expect(init.credentials).toBe('same-origin');
  });

  it('sends no Authorization header without a token', async () => {
    const fetchMock = stubFetch(() => json({}));
    await customFetch('/api/v1/facilities');
    expect(auth(fetchMock.mock.calls[0]?.[1] as RequestInit)).toBeNull();
  });

  it('handles 204 and empty bodies', async () => {
    stubFetch(() => new Response(null, { status: 204 }));
    const result = await customFetch<{ data: undefined; status: number }>('/api/v1/x');
    expect(result.status).toBe(204);
    expect(result.data).toBeUndefined();
  });
});

describe('customFetch: błędy', () => {
  it('maps Problem Details to ApiError', async () => {
    stubFetch(() =>
      problem(409, {
        type: '/problems/slot-taken',
        title: 'Konflikt',
        detail: 'Termin zajęty',
        traceId: 'trace-abc-123',
        errors: [{ field: 'start', message: 'zajęty' }],
      }),
    );

    const error = await customFetch('/api/v1/appointments').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const api = error as ApiError;
    expect(api.status).toBe(409);
    expect(api.type).toBe('slot-taken');
    expect(api.requestId).toBe('trace-abc-123');
    expect(api.isConflict()).toBe(true);
    expect(api.fieldErrors()).toEqual({ start: 'zajęty' });
  });

  it('falls back to X-Request-Id header when there is no traceId', async () => {
    stubFetch(() => json({}, 500, { 'X-Request-Id': 'hdr-id-0001' }));
    const error = (await customFetch('/api/v1/x').catch((e: unknown) => e)) as ApiError;
    expect(error.requestId).toBe('hdr-id-0001');
    expect(error.isServerError()).toBe(true);
  });

  it('reports network failures', async () => {
    vi.stubGlobal('fetch', () => Promise.reject(new TypeError('Failed to fetch')));
    const error = (await customFetch('/api/v1/x').catch((e: unknown) => e)) as ApiError;
    expect(error.isNetwork).toBe(true);
  });

  it('distinguishes caller abort from timeout', async () => {
    vi.stubGlobal(
      'fetch',
      (_: unknown, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => {
            reject(new DOMException('aborted', 'AbortError'));
          });
        }),
    );
    const controller = new AbortController();
    const pending = customFetch('/api/v1/x', { signal: controller.signal }).catch(
      (e: unknown) => e,
    );
    controller.abort();
    const error = (await pending) as ApiError;
    expect(error.isAborted).toBe(true);
    expect(error.isTimeout).toBe(false);
  });

  it('times out after the limit', async () => {
    vi.useFakeTimers();
    vi.stubGlobal(
      'fetch',
      (_: unknown, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () => {
            reject(new DOMException('aborted', 'AbortError'));
          });
        }),
    );
    const pending = customFetch('/api/v1/x', { timeoutMs: 1000 }).catch((e: unknown) => e);
    await vi.advanceTimersByTimeAsync(1001);
    const error = (await pending) as ApiError;
    expect(error.isTimeout).toBe(true);
    expect(error.isAborted).toBe(false);
  });

  it('publishes the slow-request event after 5 s and clears it when done', async () => {
    vi.useFakeTimers();
    let release: () => void = () => undefined;
    vi.stubGlobal(
      'fetch',
      () =>
        new Promise<Response>((resolve) => {
          release = () => {
            resolve(json({}));
          };
        }),
    );
    const seen: number[] = [];
    const off = slowRequests.subscribe((n) => seen.push(n));

    const pending = customFetch('/api/v1/x');
    await vi.advanceTimersByTimeAsync(SLOW_AFTER_MS + 1);
    expect(slowRequests.get()).toBe(1);
    release();
    await pending;

    expect(slowRequests.get()).toBe(0);
    expect(seen).toEqual([1, 0]);
    off();
  });
});

describe('customFetch: odświeżanie przy 401', () => {
  it('refreshes once for N parallel 401s (single-flight) and retries each request', async () => {
    tokenStore.set('old');
    let refreshCalls = 0;
    const fetchMock = stubFetch((url, init) => {
      if (url.endsWith('/auth/refresh')) {
        refreshCalls += 1;
        return json({ accessToken: 'new' });
      }
      return auth(init) === 'Bearer new' ? json({ ok: url }) : problem(401, {});
    });

    const urls = ['/api/v1/a', '/api/v1/b', '/api/v1/c', '/api/v1/d', '/api/v1/e'];
    const results = await Promise.all(urls.map((u) => customFetch<{ data: { ok: string } }>(u)));

    expect(results.map((r) => r.data.ok)).toEqual(urls);
    expect(refreshCalls).toBe(1);
    expect(tokenStore.get()).toBe('new');
    // 5 pierwszych prób + 1 refresh + 5 ponowień
    expect(fetchMock).toHaveBeenCalledTimes(11);
  });

  it('does not loop when /auth/refresh itself returns 401', async () => {
    tokenStore.set('old');
    const fetchMock = stubFetch(() => problem(401, {}));
    const expired = vi.fn();
    const off = sessionExpired.subscribe(expired);

    const error = (await customFetch('/api/v1/auth/refresh', { method: 'POST' }).catch(
      (e: unknown) => e,
    )) as ApiError;

    expect(error.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(expired).not.toHaveBeenCalled();
    off();
  });

  it('emits sessionExpired, clears the token and rejects when refresh fails', async () => {
    tokenStore.set('old');
    stubFetch((url) => (url.endsWith('/auth/refresh') ? problem(401, {}) : problem(401, {})));
    const expired = vi.fn();
    const off = sessionExpired.subscribe(expired);

    const error = (await customFetch('/api/v1/me').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(401);
    expect(expired).toHaveBeenCalledOnce();
    expect(tokenStore.get()).toBeNull();
    off();
  });

  it('retries only once (second 401 after a successful refresh is final)', async () => {
    tokenStore.set('old');
    const fetchMock = stubFetch((url) =>
      url.endsWith('/auth/refresh') ? json({ accessToken: 'new' }) : problem(401, {}),
    );
    const error = (await customFetch('/api/v1/x').catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(3); // żądanie, refresh, ponowienie
  });
});
