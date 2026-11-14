import { describe, expect, it } from 'vitest';
import { describeError } from './index';
import { ApiError } from '@/shared/api/apiError';

const http = (status: number, extra: Partial<ConstructorParameters<typeof ApiError>[0]> = {}) =>
  new ApiError({ status, requestId: 'req-1234', ...extra });

describe('describeError', () => {
  it.each([
    [401, 'Wymagane logowanie'],
    [403, 'Brak uprawnień'],
    [404, 'Nie znaleziono'],
    [409, 'Konflikt'],
    [422, 'Niepoprawne dane'],
    [429, 'Zbyt wiele żądań'],
    [500, 'Błąd serwera'],
    [503, 'Błąd serwera'],
  ])('maps status %i to "%s"', (status, title) => {
    expect(describeError(http(status)).title).toBe(title);
  });

  it('maps known problem types', () => {
    expect(describeError(http(409, { type: 'slot-taken' })).title).toBe('Termin jest już zajęty');
    expect(describeError(http(423, { type: 'account-locked' })).title).toBe(
      'Konto jest czasowo zablokowane',
    );
    expect(describeError(http(400, { type: 'validation' })).title).toBe('Niepoprawne dane');
  });

  it('falls back to a generic message with request id for unknown types', () => {
    const described = describeError(http(418, { type: 'nieznany-typ' }));
    expect(described.title).toBe('Coś poszło nie tak');
    expect(described.requestId).toBe('req-1234');
  });

  it('describes network and timeout errors', () => {
    expect(describeError(ApiError.network()).title).toBe('Brak połączenia');
    expect(describeError(ApiError.timeout()).title).toBe('Serwer nie odpowiada');
  });

  it('never exposes the server detail text', () => {
    const described = describeError(
      http(500, { detail: 'ERROR: relation "patients" does not exist', title: 'SQL error' }),
    );
    expect(JSON.stringify(described)).not.toContain('relation');
    expect(JSON.stringify(described)).not.toContain('SQL');
  });

  it('handles non-API errors', () => {
    expect(describeError(new Error('boom')).title).toBe('Coś poszło nie tak');
    expect(describeError('string').requestId).toBeUndefined();
  });
});
