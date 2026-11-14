import { http, HttpResponse } from 'msw';
import { testData } from './testData';

/**
 * Handlery bazowe napisane ręcznie (plan backendu 1a, sekcja 6.1). Po zadaniu F1 (1a) zastępowane
 * handlerami generowanymi przez Orval. Domyślnie: brak sesji (refresh -> 401).
 */
export const baseHandlers = [
  http.get('/api/v1/facilities', () =>
    HttpResponse.json([
      { slug: 'klinika-a', name: 'Klinika A' },
      { slug: 'klinika-b', name: 'Klinika B' },
    ]),
  ),
  http.get('/api/v1/health', () => HttpResponse.json({ status: 'UP' })),
  http.post('/api/v1/auth/refresh', () =>
    HttpResponse.json(testData.problem('unauthorized', 401), {
      status: 401,
      headers: { 'Content-Type': 'application/problem+json' },
    }),
  ),
  http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
  http.get('/api/v1/me', () => HttpResponse.json(testData.user('PATIENT'))),
];
