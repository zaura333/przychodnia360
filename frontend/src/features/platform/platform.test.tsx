import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { slugSchema } from './schemas';
import { createTestRouter } from '@/app/router';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { problemResponse } from '@/shared/test/testData';

const tenants = [
  { id: 't-1', name: 'Klinika A', slug: 'klinika-a', active: true },
  { id: 't-2', name: 'Klinika B', slug: 'klinika-b', active: false },
];

describe('placówki platformy', () => {
  it('lists tenants with status badges', async () => {
    server.use(http.get('/api/v1/platform/tenants', () => HttpResponse.json(tenants)));
    renderApp(createTestRouter(['/platforma/placowki']), 'PLATFORM_ADMIN');
    expect(await screen.findByText('Klinika A')).toBeVisible();
    expect(screen.getByText('Aktywna')).toBeVisible();
    expect(screen.getByText('Nieaktywna')).toBeVisible();
  });

  it('creates a tenant and reports a taken slug on the slug field', async () => {
    let attempts = 0;
    server.use(
      http.get('/api/v1/platform/tenants', () => HttpResponse.json(tenants)),
      http.post('/api/v1/platform/tenants', () => {
        attempts += 1;
        return problemResponse('slug-taken', 409);
      }),
    );
    renderApp(createTestRouter(['/platforma/placowki']), 'PLATFORM_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dodaj placówkę' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nowa placówka' });
    const d = within(dialog);
    await user.type(d.getByLabelText('Nazwa placówki'), 'Klinika C');
    await user.type(d.getByLabelText('Kod placówki'), 'klinika-a');
    await user.click(d.getByRole('button', { name: 'Dodaj placówkę' }));
    expect(await d.findByText('Ten kod jest już zajęty')).toBeVisible();
    expect(attempts).toBe(1);
  });

  it('rejects an invalid slug before calling the API', async () => {
    server.use(http.get('/api/v1/platform/tenants', () => HttpResponse.json(tenants)));
    renderApp(createTestRouter(['/platforma/placowki']), 'PLATFORM_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dodaj placówkę' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nowa placówka' });
    await user.type(within(dialog).getByLabelText('Nazwa placówki'), 'X');
    await user.type(within(dialog).getByLabelText('Kod placówki'), 'Zły Kod');
    await user.click(within(dialog).getByRole('button', { name: 'Dodaj placówkę' }));
    expect(await within(dialog).findByText(/Kod: małe litery, cyfry i myślniki/)).toBeVisible();
  });

  it('deactivates a tenant after explaining the effect', async () => {
    let patched: unknown;
    server.use(
      http.get('/api/v1/platform/tenants', () => HttpResponse.json(tenants)),
      http.patch('/api/v1/platform/tenants/t-1', async ({ request }) => {
        patched = await request.json();
        return HttpResponse.json({ ...tenants[0], active: false });
      }),
    );
    renderApp(createTestRouter(['/platforma/placowki']), 'PLATFORM_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dezaktywuj placówkę Klinika A' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/nie będą mogli się zalogować/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Dezaktywuj placówkę' }));
    await waitFor(() => {
      expect(patched).toEqual({ active: false });
    });
  });

  it('creates a tenant admin with an e-mail only (no password field)', async () => {
    let body: unknown;
    server.use(
      http.get('/api/v1/platform/tenants', () => HttpResponse.json(tenants)),
      http.post('/api/v1/platform/tenants/t-1/admins', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(
          { userId: 'u-1', email: 'admin@klinika-a.example.test' },
          { status: 201 },
        );
      }),
    );
    renderApp(createTestRouter(['/platforma/placowki']), 'PLATFORM_ADMIN');
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole('button', { name: 'Dodaj administratora placówki Klinika A' }),
    );
    const dialog = await screen.findByRole('dialog', { name: 'Administrator placówki' });
    expect(within(dialog).queryByLabelText(/hasło/i)).not.toBeInTheDocument();
    await user.type(
      within(dialog).getByLabelText('Adres e-mail administratora'),
      'admin@klinika-a.example.test',
    );
    await user.click(within(dialog).getByRole('button', { name: 'Utwórz konto' }));
    expect(await within(dialog).findByText(/linkiem do ustawienia hasła/)).toBeVisible();
    expect(body).toEqual({ email: 'admin@klinika-a.example.test' });
  });

  it('has no requests besides the tenants list', async () => {
    const urls: string[] = [];
    server.use(
      http.get('/api/v1/platform/tenants', () => HttpResponse.json(tenants)),
      http.all('/api/v1/*', ({ request }) => {
        urls.push(new URL(request.url).pathname);
        return problemResponse('not-found', 404);
      }),
    );
    renderApp(createTestRouter(['/platforma/placowki']), 'PLATFORM_ADMIN');
    await screen.findByText('Klinika A');
    expect(urls).toEqual([]);
  });
});

describe('slugSchema', () => {
  it.each(['klinika-a', 'ab', 'a1-b2'])('accepts %s', (s) => {
    expect(slugSchema.safeParse(s).success).toBe(true);
  });
  it.each(['a', '-ab', 'Klinika', 'za spacją', 'a'.repeat(64)])('rejects %s', (s) => {
    expect(slugSchema.safeParse(s).success).toBe(false);
  });
});
