import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { createTestRouter } from '@/app/router';
import { passwordSchema } from '@/shared/lib/schemas/password';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { meResponse, problemResponse } from '@/shared/test/testData';

async function selectTenant(user: ReturnType<typeof userEvent.setup>) {
  await screen.findByRole('option', { name: 'Klinika A' });
  await user.selectOptions(screen.getByLabelText('Placówka'), 'klinika-a');
}

async function fillLogin(user: ReturnType<typeof userEvent.setup>) {
  await selectTenant(user);
  await user.type(screen.getByLabelText('Adres e-mail'), 'a@klinika-a.example.test');
  await user.type(screen.getByLabelText('Hasło'), 'demo-przychodnia-2026');
  await user.click(screen.getByRole('button', { name: 'Zaloguj się' }));
}

describe('logowanie', () => {
  it.each([
    ['PATIENT', '/pacjent'],
    ['RECEPTIONIST', '/pracownik'],
    ['TENANT_ADMIN', '/administracja'],
  ] as const)('redirects %s to %s after login', async (role, path) => {
    server.use(
      http.post('/api/v1/auth/login', () => HttpResponse.json({ accessToken: 'tok' })),
      http.get('/api/v1/me', () => HttpResponse.json(meResponse(role))),
    );
    const router = createTestRouter(['/logowanie']);
    renderApp(router, 'anonymous');
    await fillLogin(userEvent.setup());
    await waitFor(() => {
      expect(router.state.location.pathname).toBe(path);
    });
    expect(localStorage.getItem('p360.tenantSlug')).toBe('klinika-a');
  });

  it('shows one neutral message for wrong credentials', async () => {
    server.use(http.post('/api/v1/auth/login', () => problemResponse('invalid-credentials', 401)));
    renderApp(createTestRouter(['/logowanie']), 'anonymous');
    await fillLogin(userEvent.setup());
    expect(await screen.findByText('Nieprawidłowe dane logowania')).toBeVisible();
  });

  it('shows the rate limit message on 429', async () => {
    server.use(http.post('/api/v1/auth/login', () => problemResponse('too-many-requests', 429)));
    renderApp(createTestRouter(['/logowanie']), 'anonymous');
    await fillLogin(userEvent.setup());
    expect(await screen.findByText('Zbyt wiele żądań')).toBeVisible();
  });

  it('validates required fields before calling the API', async () => {
    renderApp(createTestRouter(['/logowanie']), 'anonymous');
    await userEvent.click(await screen.findByRole('button', { name: 'Zaloguj się' }));
    expect(await screen.findByText('Wybierz placówkę')).toBeVisible();
    expect(screen.getByText('Podaj adres e-mail')).toBeVisible();
  });

  it('returns to the remembered path after login', async () => {
    server.use(
      http.post('/api/v1/auth/login', () => HttpResponse.json({ accessToken: 'tok' })),
      http.get('/api/v1/me', () => HttpResponse.json(meResponse('PATIENT'))),
    );
    const router = createTestRouter(['/pacjent/rezerwacja']);
    renderApp(router, 'anonymous');
    await fillLogin(userEvent.setup());
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/pacjent/rezerwacja');
    });
  });
});

describe('rejestracja i reset hasła', () => {
  it('shows a neutral message after registration', async () => {
    server.use(
      http.post('/api/v1/auth/register-patient', () => new HttpResponse(null, { status: 202 })),
    );
    renderApp(createTestRouter(['/rejestracja']), 'anonymous');
    const user = userEvent.setup();
    await selectTenant(user);
    await user.type(screen.getByLabelText('Imię'), 'Anna');
    await user.type(screen.getByLabelText('Nazwisko'), 'Testowa');
    await user.type(screen.getByLabelText('Data urodzenia'), '1990-05-17');
    await user.type(screen.getByLabelText('Adres e-mail'), 'anna@example.test');
    await user.type(screen.getByLabelText('Hasło'), 'bardzo-dlugie-haslo-1');
    await user.type(screen.getByLabelText('Powtórz hasło'), 'bardzo-dlugie-haslo-1');
    await user.click(screen.getByRole('button', { name: 'Załóż konto' }));
    expect(
      await screen.findByText(
        'Jeśli adres jest wolny, konto zostało utworzone, możesz się zalogować.',
      ),
    ).toBeVisible();
  });

  it('rejects mismatching and short passwords on the client', async () => {
    renderApp(createTestRouter(['/rejestracja']), 'anonymous');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Hasło'), 'krotkie');
    await user.type(screen.getByLabelText('Powtórz hasło'), 'inne');
    await user.click(screen.getByRole('button', { name: 'Załóż konto' }));
    expect(await screen.findByText('Hasło musi mieć co najmniej 12 znaków')).toBeVisible();
    expect(screen.getByText('Hasła muszą być takie same')).toBeVisible();
  });

  it('gives the same message whether or not the account exists', async () => {
    server.use(
      http.post(
        '/api/v1/auth/password-reset/request',
        () => new HttpResponse(null, { status: 202 }),
      ),
    );
    renderApp(createTestRouter(['/nie-pamietam-hasla']), 'anonymous');
    const user = userEvent.setup();
    await selectTenant(user);
    await user.type(screen.getByLabelText('Adres e-mail'), 'ktos@example.test');
    await user.click(screen.getByRole('button', { name: 'Wyślij link' }));
    expect(
      await screen.findByText('Jeśli konto istnieje, wysłaliśmy wiadomość z linkiem.'),
    ).toBeVisible();
  });

  it('removes the token from the address bar and redirects to login after success', async () => {
    let received: unknown;
    server.use(
      http.post('/api/v1/auth/password-reset/confirm', async ({ request }) => {
        received = await request.json();
        return new HttpResponse(null, { status: 204 });
      }),
    );
    window.history.pushState({}, '', '/reset-hasla?token=abc.def');
    const router = createTestRouter(['/reset-hasla?token=abc.def']);
    renderApp(router, 'anonymous');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Nowe hasło'), 'nowe-bardzo-dlugie-haslo');
    expect(window.location.search).toBe('');
    await user.type(screen.getByLabelText('Powtórz hasło'), 'nowe-bardzo-dlugie-haslo');
    await user.click(screen.getByRole('button', { name: 'Ustaw hasło' }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/logowanie');
    });
    expect(received).toEqual({ token: 'abc.def', newPassword: 'nowe-bardzo-dlugie-haslo' });
    expect(await screen.findByText('Hasło zostało ustawione. Możesz się zalogować.')).toBeVisible();
  });

  it('offers a new link when the token is expired', async () => {
    server.use(
      http.post('/api/v1/auth/password-reset/confirm', () =>
        problemResponse('invalid-or-expired-token', 422),
      ),
    );
    renderApp(createTestRouter(['/reset-hasla?token=zly']), 'anonymous');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Nowe hasło'), 'nowe-bardzo-dlugie-haslo');
    await user.type(screen.getByLabelText('Powtórz hasło'), 'nowe-bardzo-dlugie-haslo');
    await user.click(screen.getByRole('button', { name: 'Ustaw hasło' }));
    expect(await screen.findByText('Link jest nieprawidłowy lub wygasł')).toBeVisible();
    expect(screen.getAllByRole('link', { name: 'Poproś o nowy link' }).length).toBeGreaterThan(0);
  });
});

describe('logowanie administratora platformy', () => {
  it('logs in without a tenant code and goes to the platform area', async () => {
    server.use(
      http.post('/api/v1/auth/platform/login', () => HttpResponse.json({ accessToken: 'tok' })),
      http.get('/api/v1/me', () => HttpResponse.json(meResponse('PLATFORM_ADMIN'))),
    );
    const router = createTestRouter(['/platforma/logowanie']);
    renderApp(router, 'anonymous');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Adres e-mail'), 'root@platform.example.test');
    await user.type(screen.getByLabelText('Hasło'), 'demo-przychodnia-2026');
    await user.click(screen.getByRole('button', { name: 'Zaloguj się' }));
    await waitFor(() => {
      expect(router.state.location.pathname).toBe('/platforma');
    });
  });
});

describe('passwordSchema', () => {
  it('requires 12 to 128 characters and no character classes', () => {
    expect(passwordSchema.safeParse('a'.repeat(11)).success).toBe(false);
    expect(passwordSchema.safeParse('a'.repeat(12)).success).toBe(true);
    expect(passwordSchema.safeParse('a'.repeat(129)).success).toBe(false);
  });
});
