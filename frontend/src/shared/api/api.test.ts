import { describe, expect, it } from 'vitest';
import { ApiError, problemTypeSlug } from './apiError';
import { qk, retryDelay, shouldRetry } from './queryClient';
import { tokenStore } from './tokenStore';
import { parseEnv } from '@/shared/config/env';

describe('problemTypeSlug', () => {
  it('returns the last URL segment', () => {
    expect(problemTypeSlug('/problems/slot-taken')).toBe('slot-taken');
    expect(problemTypeSlug('https://x.pl/problems/validation/')).toBe('validation');
    expect(problemTypeSlug('about:blank')).toBeUndefined();
    expect(problemTypeSlug(undefined)).toBeUndefined();
  });
});

describe('retry policy', () => {
  const http = (status: number) => new ApiError({ status });

  it('retries network and 5xx errors at most twice', () => {
    expect(shouldRetry(0, ApiError.network())).toBe(true);
    expect(shouldRetry(1, http(503))).toBe(true);
    expect(shouldRetry(2, http(503))).toBe(false);
  });

  it('never retries 4xx or unknown errors', () => {
    for (const status of [400, 401, 403, 404, 409, 422, 429]) {
      expect(shouldRetry(0, http(status))).toBe(false);
    }
    expect(shouldRetry(0, new Error('x'))).toBe(false);
    expect(shouldRetry(0, ApiError.timeout())).toBe(false);
  });

  it('uses increasing backoff', () => {
    expect([0, 1, 2, 3].map(retryDelay)).toEqual([1000, 2000, 4000, 8000]);
  });
});

describe('query keys', () => {
  it('depend on the user id', () => {
    expect(qk.user('u1').scoped('appointments', 5)).toEqual(['user', 'u1', 'appointments', 5]);
    expect(qk.user('u1').scoped('x')).not.toEqual(qk.user('u2').scoped('x'));
  });
});

describe('tokenStore', () => {
  it('keeps the token only in memory and notifies subscribers', () => {
    const seen: (string | null)[] = [];
    const off = tokenStore.subscribe((t) => seen.push(t));
    tokenStore.set('abc');
    tokenStore.clear();
    off();
    expect(seen).toEqual(['abc', null]);
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });
});

describe('env', () => {
  it('applies defaults', () => {
    expect(parseEnv({})).toEqual({
      apiBase: '/api/v1',
      featureMedicalStaff: false,
      showDemoAccounts: false,
    });
  });

  it('parses flags and trims the trailing slash', () => {
    const env = parseEnv({
      VITE_API_BASE: 'https://api.example.pl/api/v1/',
      VITE_FEATURE_MEDICAL_STAFF: 'true',
    });
    expect(env.apiBase).toBe('https://api.example.pl/api/v1');
    expect(env.featureMedicalStaff).toBe(true);
  });

  it('rejects invalid values', () => {
    expect(() => parseEnv({ VITE_FEATURE_MEDICAL_STAFF: 'tak' })).toThrow();
  });
});
