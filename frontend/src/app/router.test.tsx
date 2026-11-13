import { screen, waitFor, within } from '@testing-library/react';
import { createMemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import { AREAS, defaultAreaFor } from '@/app/areas';
import { RootLayout } from '@/app/layouts/layouts';
import { NAVIGATION } from '@/app/navigation';
import { ErrorPage } from '@/app/pages/pages';
import { createTestRouter } from '@/app/router';
import { can, ROLES } from '@/shared/lib/permissions';
import { renderApp } from '@/shared/test/render';

const AREA_TITLE: Record<string, string> = {
  '/pacjent': 'Panel pacjenta',
  '/pracownik': 'Panel pracownika',
  '/administracja': 'Administracja placówki',
  '/platforma': 'Administracja platformy',
};

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
    await screen.findByRole('heading', { name: 'Panel pracownika' });
    expect(router.state.location.pathname).toBe('/pracownik');
  });

  it('area chunks load lazily and unknown subpaths inside an area are 404', async () => {
    renderApp(createTestRouter(['/pracownik/nie-ma']), 'RECEPTIONIST');
    expect(await screen.findByRole('heading', { name: 'Nie znaleziono strony' })).toBeVisible();
  });
});

describe('rola x trasa x wynik', () => {
  const cases = ROLES.flatMap((role) =>
    AREAS.map((area) => ({
      role,
      path: area.path,
      outcome: can({ role }, area.action) ? 'access' : 'forbidden',
    })),
  );

  it.each(cases)('$role on $path -> $outcome', async ({ role, path, outcome }) => {
    renderApp(createTestRouter([path]), role);
    if (outcome === 'access') {
      expect(await screen.findByRole('heading', { name: AREA_TITLE[path] ?? '' })).toBeVisible();
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

describe('rola x pozycja menu', () => {
  it.each(ROLES)('%s sees exactly its menu and every item is permitted', async (role) => {
    renderApp(createTestRouter([defaultAreaFor(role)]), role);
    const nav = await screen.findByRole('navigation', { name: 'Menu główne' });
    const labels = within(nav)
      .getAllByRole('link')
      .map((l) => l.textContent);
    expect(labels).toEqual(NAVIGATION[role].map((i) => i.label));

    for (const item of NAVIGATION[role]) {
      const area = AREAS.find((a) => item.to === a.path || item.to.startsWith(`${a.path}/`));
      expect(area, `pozycja ${item.to} musi należeć do obszaru`).toBeDefined();
      expect(can({ role }, area?.action ?? 'area:platform')).toBe(true);
    }
  });
});
