import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, RouterProvider, type DataRouter } from 'react-router';
import { testData } from './testData';
import { AuthProvider } from '@/app/providers/AuthProvider';
import { ThemeProvider } from '@/app/providers/ThemeProvider';
import type { SessionState } from '@/shared/api/session';
import type { Role } from '@/shared/lib/permissions';

interface Options {
  route?: string;
  /** Zadany stan sesji, rola (zalogowany) albo 'anonymous' / 'loading'. */
  auth?: SessionState | Role | 'anonymous' | 'loading';
}

function toState(auth: NonNullable<Options['auth']>): SessionState {
  if (typeof auth === 'object') return auth;
  if (auth === 'anonymous') return { status: 'anonymous', backendUnavailable: false };
  if (auth === 'loading') return { status: 'loading' };
  return testData.session(auth);
}

/** Router w pamięci, QueryClient bez ponowień, AuthProvider ze zadanym stanem i ThemeProvider. */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', auth = 'anonymous' }: Options = {},
): RenderResult & { queryClient: QueryClient } {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider initialState={toState(auth)}>
          <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}

/** Renderuje całą aplikację z routerem w pamięci i zadanym stanem sesji. */
export function renderApp(
  router: DataRouter,
  auth: NonNullable<Options['auth']> = 'anonymous',
): RenderResult & { queryClient: QueryClient } {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const result = render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider initialState={toState(auth)}>
          <RouterProvider router={router} />
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
  return { ...result, queryClient };
}
