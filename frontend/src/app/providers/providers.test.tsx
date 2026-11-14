import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from './AuthProvider';
import { ThemeProvider, useTheme } from './ThemeProvider';
import { sessionExpired } from '@/shared/api/events';
import { useAuth } from '@/shared/api/session';
import { server } from '@/shared/test/server';
import { testData } from '@/shared/test/testData';
import { isSafeInternalPath } from '@/shared/lib/safe-path';

function Probe() {
  const auth = useAuth();
  return (
    <div>
      <p data-testid="status">{auth.status}</p>
      <p data-testid="role">{auth.user?.role ?? '-'}</p>
      <p data-testid="unavailable">{String(auth.backendUnavailable)}</p>
      <button
        onClick={() => {
          void auth.logout();
        }}
      >
        wyloguj
      </button>
      <button
        onClick={() => {
          void auth.login({ accessToken: 'tok', user: testData.user('DOCTOR') });
        }}
      >
        zaloguj lekarza
      </button>
    </div>
  );
}

function setup(ui: ReactNode = <Probe />, queryClient = new QueryClient()) {
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{ui}</AuthProvider>
    </QueryClientProvider>,
  );
  return queryClient;
}

const status = () => screen.getByTestId('status').textContent;

describe('AuthProvider: start sesji', () => {
  it('silent refresh succeeds -> authenticated with the role from /me', async () => {
    server.use(
      http.post('/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: 'tok-1' })),
      http.get('/api/v1/me', ({ request }) =>
        request.headers.get('Authorization') === 'Bearer tok-1'
          ? HttpResponse.json(testData.user('RECEPTIONIST'))
          : new HttpResponse(null, { status: 401 }),
      ),
    );
    setup();
    expect(status()).toBe('loading');
    await waitFor(() => {
      expect(status()).toBe('authenticated');
    });
    expect(screen.getByTestId('role')).toHaveTextContent('RECEPTIONIST');
  });

  it('refresh 401 -> quiet anonymous without the unavailable flag', async () => {
    setup();
    await waitFor(() => {
      expect(status()).toBe('anonymous');
    });
    expect(screen.getByTestId('unavailable')).toHaveTextContent('false');
  });

  it('network error -> anonymous with backendUnavailable', async () => {
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.error()));
    setup();
    await waitFor(() => {
      expect(status()).toBe('anonymous');
    });
    expect(screen.getByTestId('unavailable')).toHaveTextContent('true');
  });

  it('5xx -> anonymous with backendUnavailable', async () => {
    server.use(
      http.post('/api/v1/auth/refresh', () =>
        HttpResponse.json(testData.problem('internal', 503), { status: 503 }),
      ),
    );
    setup();
    await waitFor(() => {
      expect(screen.getByTestId('unavailable')).toHaveTextContent('true');
    });
  });
});

describe('AuthProvider: czyszczenie cache', () => {
  it('logout clears the query cache', async () => {
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: 'tok' })));
    const queryClient = setup();
    await waitFor(() => {
      expect(status()).toBe('authenticated');
    });
    queryClient.setQueryData(['user', 'user-patient', 'x'], { secret: 1 });

    await userEvent.click(screen.getByRole('button', { name: 'wyloguj' }));

    await waitFor(() => {
      expect(status()).toBe('anonymous');
    });
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
  });

  it('sessionExpired clears the cache and the session', async () => {
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: 'tok' })));
    const queryClient = setup();
    await waitFor(() => {
      expect(status()).toBe('authenticated');
    });
    queryClient.setQueryData(['x'], 1);

    act(() => {
      sessionExpired.emit();
    });

    await waitFor(() => {
      expect(status()).toBe('anonymous');
    });
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
  });

  it('a change of user clears the cache', async () => {
    server.use(http.post('/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: 'tok' })));
    const queryClient = setup();
    await waitFor(() => {
      expect(status()).toBe('authenticated');
    });
    queryClient.setQueryData(['user', 'user-patient', 'x'], { secret: 1 });

    await userEvent.click(screen.getByRole('button', { name: 'zaloguj lekarza' }));

    await waitFor(() => {
      expect(screen.getByTestId('role')).toHaveTextContent('DOCTOR');
    });
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
  });
});

describe('ThemeProvider', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  function ThemeProbe() {
    const { preference, resolved, setPreference } = useTheme();
    return (
      <div>
        <p data-testid="pref">{preference}</p>
        <p data-testid="resolved">{resolved}</p>
        <button
          onClick={() => {
            setPreference('dark');
          }}
        >
          ciemny
        </button>
      </div>
    );
  }

  it('applies and persists the preference', async () => {
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );
    await userEvent.click(screen.getByRole('button', { name: 'ciemny' }));
    expect(document.documentElement).toHaveClass('dark');
    expect(window.localStorage.getItem('p360_theme')).toBe('dark');
  });

  it('does not fail when storage is unavailable', async () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    render(
      <ThemeProvider>
        <ThemeProbe />
      </ThemeProvider>,
    );
    expect(screen.getByTestId('pref')).toHaveTextContent('system');
    await userEvent.click(screen.getByRole('button', { name: 'ciemny' }));
    expect(document.documentElement).toHaveClass('dark');
  });
});

describe('isSafeInternalPath', () => {
  it('accepts only internal paths', () => {
    expect(isSafeInternalPath('/pracownik')).toBe(true);
    expect(isSafeInternalPath('/pacjent/wizyty?x=1')).toBe(true);
    for (const bad of [
      '//evil.example',
      'https://evil.example',
      '/\\evil.example',
      '/javascript:alert(1)',
      'pracownik',
      '',
      null,
      42,
    ]) {
      expect(isSafeInternalPath(bad)).toBe(false);
    }
  });
});
