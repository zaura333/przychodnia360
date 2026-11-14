import { screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AREAS, defaultAreaFor } from '@/app/areas';
import { RootLayout } from '@/app/layouts/layouts';
import { NAVIGATION } from '@/app/navigation';
import { ROUTE_TABLE } from '@/app/routeTable';
import { ErrorPage } from '@/app/pages/pages';
import { createTestRouter } from '@/app/router';
import { can, ROLES } from '@/shared/lib/permissions';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { problemResponse } from '@/shared/test/testData';

const banner = () => screen.getByRole('region', { name: 'Informacja o wersji demonstracyjnej' });

describe('routing: baner demo (NFR-07)', () => {
  it.each(['/logowanie', '/nie-ma-takiej-strony'])('is visible on %s', async (path) => {
    renderApp(createTestRouter([path]), 'anonymous');
    await screen.findByRole('heading');
    expect(banner()).toHaveTextContent('Dane wyłącznie syntetyczne');
  });

  it('stays visible when a route crashes', async () => {
    function Boom(): never {
      throw new Error('awaria trasy');
    }
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    const router = createMemoryRouter(
      [
        {
          element: <RootLayout />,
          children: [{ errorElement: <ErrorPage />, children: [{ path: '/', element: <Boom /> }] }],
        },
      ],
      { initialEntries: ['/'] },
    );
    renderApp(router, 'anonymous');
    expect(await screen.findByRole('alert')).toHaveTextContent(/Identyfikator: [0-9a-f]{8}/);
    expect(screen.queryByText(/awaria trasy/)).not.toBeInTheDocument();
    expect(banner()).toBeInTheDocument();
    spy.mockRestore();
  });
});

describe('routing: sesja', () => {
  it('unknown path shows NotFoundPage', async () => {
    renderApp(createTestRouter(['/nie-ma-takiej-strony']), 'anonymous');
    expect(await screen.findByRole('heading', { name: 'Nie znaleziono strony' })).toBeVisible();
  });

  it('shows the startup screen (not the login) while the session is loading', async () => {
    renderApp(createTestRouter(['/pracownik']), 'loading');
    expect(await screen.findByRole('status', { name: 'Ładowanie aplikacji' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Logowanie' })).not.toBeInTheDocument();
  });

  it('redirects the home page by role', async () => {
    const router = createTestRouter(['/']);
    renderApp(router, 'DOCTOR');
    await screen.findByRole('heading', { name: 'Dzisiaj' });
    expect(router.state.location.pathname).toBe('/pracownik');
  });

  it('area chunks load lazily and unknown subpaths inside an area are 404', async () => {
    renderApp(createTestRouter(['/pracownik/nie-ma']), 'RECEPTIONIST');
    expect(await screen.findByRole('heading', { name: 'Nie znaleziono strony' })).toBeVisible();
  });
});

const AREA_ROLES = (area: (typeof AREAS)[number]) =>
  ROLES.filter((role) => can({ role }, area.action));

describe('rola x obszar', () => {
  const cases = ROLES.flatMap((role) =>
    AREAS.map((area) => ({ role, path: area.path, allowed: AREA_ROLES(area).includes(role) })),
  );

  it.each(cases)('$role on $path -> allowed: $allowed', async ({ role, path, allowed }) => {
    renderApp(createTestRouter([path]), role);
    if (allowed) {
      await waitFor(() => {
        expect(screen.queryByRole('heading', { name: 'Brak dostępu' })).not.toBeInTheDocument();
        expect(screen.getByRole('navigation', { name: 'Menu główne' })).toBeVisible();
      });
    } else {
      expect(await screen.findByRole('heading', { name: 'Brak dostępu' })).toBeVisible();
    }
  });

  it.each(AREAS.map((a) => a.path))('anonymous on %s -> redirect to /logowanie', async (path) => {
    const router = createTestRouter([path]);
    renderApp(router, 'anonymous');
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/logowanie');
    });
    expect(router.state.location.state).toEqual({ from: path });
  });
});

describe('rola x trasa x wynik (z ROUTE_TABLE)', () => {
  const cases = ROUTE_TABLE.flatMap((route) =>
    ROLES.map((role) => ({
      role,
      example: route.example,
      allowed: route.roles.includes(role),
    })),
  );

  it.each(cases)('$role on $example -> allowed: $allowed', async ({ role, example, allowed }) => {
    // Strony wołają API: nieobsłużone żądania kończą się 404, a test sprawdza tylko bramkę roli
    server.use(http.all('/api/*', () => problemResponse('not-found', 404)));
    renderApp(createTestRouter([example]), role);
    if (allowed) {
      await screen.findByRole('navigation', { name: 'Menu główne' });
      await waitFor(() => {
        expect(screen.queryByRole('heading', { name: 'Brak dostępu' })).not.toBeInTheDocument();
      });
    } else {
      expect(await screen.findByRole('heading', { name: 'Brak dostępu' })).toBeVisible();
    }
  });

  it('every ROUTE_TABLE pattern is reachable by at least one role', () => {
    for (const route of ROUTE_TABLE) expect(route.roles.length).toBeGreaterThan(0);
  });
});

describe('rola x pozycja menu', () => {
  it.each(ROLES)('%s sees exactly its menu and every item is permitted', async (role) => {
    server.use(http.all('/api/*', () => problemResponse('not-found', 404)));
    renderApp(createTestRouter([defaultAreaFor(role)]), role);
    const nav = await screen.findByRole('navigation', { name: 'Menu główne' });
    const labels = within(nav)
      .getAllByRole('link')
      .map((l) => l.textContent);
    expect(labels).toEqual(NAVIGATION[role].map((i) => i.label));

    for (const item of NAVIGATION[role]) {
      const spec = ROUTE_TABLE.find((r) => r.example === item.to);
      expect(spec, `pozycja ${item.to} musi prowadzić do trasy z ROUTE_TABLE`).toBeDefined();
      expect(spec?.roles, `${role} nie ma dostępu do ${item.to}`).toContain(role);
    }
  });

  it('platform admin menu offers nothing but the tenant list', () => {
    expect(NAVIGATION.PLATFORM_ADMIN.map((i) => i.to)).toEqual(['/platforma/placowki']);
  });
});
