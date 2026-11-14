import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, it } from 'vitest';
import { ROUTE_TABLE } from '@/app/routeTable';
import { createTestRouter } from '@/app/router';
import { expectNoA11yViolations } from '@/shared/test/a11y';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { page, testData } from '@/shared/test/testData';

/**
 * Plan fali 0, A4: axe na każdej trasie z ROUTE_TABLE, dla każdej roli z dostępem.
 * Dane z API są puste (lista `[]`), więc sprawdzamy stan pusty/komunikat błędu danej trasy.
 * Stany z danymi pokrywają testy ekranów oraz siatka terminarza (ADR-F10). Nowa trasa w tabeli
 * trafia tu automatycznie.
 */
const cases = ROUTE_TABLE.flatMap((spec) =>
  spec.roles.map((role) => ({ path: spec.path, example: spec.example, role })),
);

describe('dostępność (axe): wszystkie trasy z ROUTE_TABLE', () => {
  it.each(cases)('$role na $path nie ma naruszeń', async ({ example, role }) => {
    server.use(
      http.get('/api/v1/*', ({ request }) => {
        const { pathname } = new URL(request.url);
        if (pathname.endsWith('/facilities') || pathname.endsWith('/health')) return undefined;
        // Lista usług jest stronicowana; szczegóły to obiekt (pusta tablica dałaby pusty nagłówek)
        if (/\/services\/[^/]+$/.test(pathname)) return HttpResponse.json(testData.service());
        if (pathname.endsWith('/services')) return HttpResponse.json(page([]));
        return HttpResponse.json([]);
      }),
    );
    const { container } = renderApp(createTestRouter([example]), role);
    // Strona zaczyna się od nagłówka głównego; czekamy aż skończy się ładowanie danych
    await screen.findByRole('heading', { level: 1 });
    await waitFor(() => {
      if (container.querySelector('[aria-busy="true"], [role="status"][aria-label*="Ładowanie"]')) {
        throw new Error('trwa ładowanie');
      }
    });
    await expectNoA11yViolations(container);
  });
});
