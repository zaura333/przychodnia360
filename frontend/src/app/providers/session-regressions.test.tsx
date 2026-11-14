import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { useAuth } from '@/shared/api/session';
import { server } from '@/shared/test/server';
import { problemResponse, testData } from '@/shared/test/testData';

/** Błędy znalezione w e2e na prawdziwym backendzie (docs/bugs-and-fixes). */

function PublicList() {
  const query = useQuery({
    queryKey: ['public', 'list'],
    queryFn: async () => {
      const response = await fetch('/api/v1/facilities');
      return (await response.json()) as { slug: string }[];
    },
  });
  return (
    <p data-testid="list">
      {query.isPending ? 'loading' : query.data?.map((f) => f.slug).join(',')}
    </p>
  );
}

describe('start bez sesji', () => {
  it('does not wipe an in-flight public query when the silent refresh fails', async () => {
    server.use(
      http.post('/api/v1/auth/refresh', async () => {
        await new Promise((r) => setTimeout(r, 30));
        return problemResponse('invalid-refresh-token', 401);
      }),
      http.get('/api/v1/facilities', async () => {
        await new Promise((r) => setTimeout(r, 80));
        return HttpResponse.json([{ slug: 'klinika-a', name: 'A' }]);
      }),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <AuthProvider>
          <PublicList />
        </AuthProvider>
      </QueryClientProvider>,
    );
    // Odświeżenie kończy się przed odpowiedzią zapytania publicznego; wynik musi mimo to dotrzeć do widoku
    await waitFor(() => {
      expect(screen.getByTestId('list')).toHaveTextContent('klinika-a');
    });
  });
});

function LogoutButton() {
  const auth = useAuth();
  return (
    <button
      onClick={() => {
        void auth.logout();
      }}
    >
      wyloguj
    </button>
  );
}

describe('wylogowanie', () => {
  it('sends the access token so the backend can revoke the refresh token family', async () => {
    let header: string | null = null;
    server.use(
      http.post('/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: 'tok-123' })),
      http.get('/api/v1/me', () =>
        HttpResponse.json({ ...testData.user('PATIENT'), role: 'PATIENT' }),
      ),
      http.post('/api/v1/auth/logout', ({ request }) => {
        header = request.headers.get('Authorization');
        return new HttpResponse(null, { status: 204 });
      }),
    );
    render(
      <QueryClientProvider client={new QueryClient()}>
        <AuthProvider>
          <LogoutButton />
        </AuthProvider>
      </QueryClientProvider>,
    );
    await userEvent.click(await screen.findByRole('button', { name: 'wyloguj' }));
    await waitFor(() => {
      expect(header).toBe('Bearer tok-123');
    });
  });
});
