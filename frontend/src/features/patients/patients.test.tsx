import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { createTestRouter } from '@/app/router';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { page, problemResponse } from '@/shared/test/testData';

const patient = (overrides: Record<string, unknown> = {}) => ({
  id: 'p-1',
  firstName: 'Anna',
  lastName: 'Nowak',
  birthDate: '1990-05-17',
  contactEmail: 'anna@example.test',
  hasAccount: false,
  ...overrides,
});

describe('wyszukiwanie pacjentów (rejestrator)', () => {
  it('searches by POST body only after two characters and never puts the phrase in the URL', async () => {
    const calls: { url: string; body: unknown }[] = [];
    server.use(
      http.post('/api/v1/reception/patients/search', async ({ request }) => {
        calls.push({ url: request.url, body: await request.json() });
        return HttpResponse.json(page([patient()], { size: 20 }));
      }),
    );
    const router = createTestRouter(['/pracownik/pacjenci']);
    renderApp(router, 'RECEPTIONIST');
    const user = userEvent.setup();
    const input = await screen.findByLabelText('Szukaj pacjenta');
    await user.type(input, 'A');
    await new Promise((r) => setTimeout(r, 400));
    expect(calls).toHaveLength(0);
    await user.type(input, 'nn');
    expect(await screen.findByText('Anna Nowak')).toBeVisible();
    expect(screen.getByText(/ur\. 17\.05\.1990/)).toBeVisible();
    expect(calls).toHaveLength(1);
    expect(calls[0]?.body).toMatchObject({ query: 'Ann' });
    expect(calls[0]?.url).not.toContain('Ann');
    expect(router.state.location.search).toBe('');
  });

  it('debounces typing into a single request', async () => {
    let count = 0;
    server.use(
      http.post('/api/v1/reception/patients/search', () => {
        count += 1;
        return HttpResponse.json(page([patient()], { size: 20 }));
      }),
    );
    renderApp(createTestRouter(['/pracownik/pacjenci']), 'RECEPTIONIST');
    await userEvent.type(await screen.findByLabelText('Szukaj pacjenta'), 'Nowak');
    await screen.findByText('Anna Nowak');
    expect(count).toBe(1);
  });

  it('shows an empty state for no matches', async () => {
    server.use(
      http.post('/api/v1/reception/patients/search', () =>
        HttpResponse.json(page([], { size: 20 })),
      ),
    );
    renderApp(createTestRouter(['/pracownik/pacjenci']), 'RECEPTIONIST');
    await userEvent.type(await screen.findByLabelText('Szukaj pacjenta'), 'Zzz');
    expect(await screen.findByText('Nie znaleziono pacjentów')).toBeVisible();
  });
});

describe('zakładanie i edycja pacjenta', () => {
  it('creates a patient with optional contact email', async () => {
    let body: unknown;
    server.use(
      http.post('/api/v1/reception/patients', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(patient({ contactEmail: null }), { status: 201 });
      }),
    );
    renderApp(createTestRouter(['/pracownik/pacjenci']), 'RECEPTIONIST');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Nowy pacjent' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nowy pacjent' });
    const d = within(dialog);
    await user.type(d.getByLabelText('Imię'), 'Anna');
    await user.type(d.getByLabelText('Nazwisko'), 'Nowak');
    await user.type(d.getByLabelText('Data urodzenia'), '1990-05-17');
    await user.click(d.getByRole('button', { name: 'Zapisz pacjenta' }));
    await waitFor(() => {
      expect(body).toEqual({ firstName: 'Anna', lastName: 'Nowak', birthDate: '1990-05-17' });
    });
  });

  it('validates the patient form', async () => {
    renderApp(createTestRouter(['/pracownik/pacjenci']), 'RECEPTIONIST');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Nowy pacjent' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nowy pacjent' });
    await user.click(within(dialog).getByRole('button', { name: 'Zapisz pacjenta' }));
    expect(await within(dialog).findByText('Podaj imię')).toBeVisible();
    expect(within(dialog).getByText('Podaj nazwisko')).toBeVisible();
    expect(within(dialog).getByText('Podaj datę urodzenia')).toBeVisible();
  });

  it('edits only the contact data of a found patient', async () => {
    let received: { id: string; body: unknown } | undefined;
    server.use(
      http.post('/api/v1/reception/patients/search', () =>
        HttpResponse.json(page([patient()], { size: 20 })),
      ),
      http.patch('/api/v1/reception/patients/:id/contact', async ({ params, request }) => {
        received = { id: String(params.id), body: await request.json() };
        return HttpResponse.json(patient({ contactEmail: 'nowy@example.test' }));
      }),
    );
    renderApp(createTestRouter(['/pracownik/pacjenci']), 'RECEPTIONIST');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Szukaj pacjenta'), 'Nowak');
    await user.click(await screen.findByRole('button', { name: 'Edytuj kontakt: Anna Nowak' }));
    const dialog = await screen.findByRole('dialog', { name: 'Dane kontaktowe pacjenta' });
    const email = within(dialog).getByLabelText('Adres e-mail do kontaktu');
    await user.clear(email);
    await user.type(email, 'nowy@example.test');
    await user.click(within(dialog).getByRole('button', { name: 'Zapisz' }));
    await waitFor(() => {
      expect(received).toEqual({ id: 'p-1', body: { contactEmail: 'nowy@example.test' } });
    });
  });
});

describe('moje dane pacjenta', () => {
  it('shows read-only identity data and saves the contact email', async () => {
    let body: unknown;
    server.use(
      http.get('/api/v1/me/patient', () => HttpResponse.json(patient())),
      http.patch('/api/v1/me/patient', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(patient({ contactEmail: 'inny@example.test' }));
      }),
    );
    renderApp(createTestRouter(['/pacjent/moje-dane']), 'PATIENT');
    expect(await screen.findByText('Anna')).toBeVisible();
    expect(screen.getByText('17.05.1990')).toBeVisible();
    expect(screen.getByText('Zmiana tych danych przez rejestrację.')).toBeVisible();
    const user = userEvent.setup();
    const email = screen.getByLabelText('Adres e-mail do kontaktu');
    await user.clear(email);
    await user.type(email, 'inny@example.test');
    await user.click(screen.getByRole('button', { name: 'Zapisz' }));
    await waitFor(() => {
      expect(body).toEqual({ contactEmail: 'inny@example.test' });
    });
    expect(await screen.findByText('Zapisano zmiany')).toBeVisible();
  });

  it('reports a patient without a patient record', async () => {
    server.use(http.get('/api/v1/me/patient', () => problemResponse('no-patient-record', 404)));
    renderApp(createTestRouter(['/pacjent/moje-dane']), 'PATIENT');
    expect(await screen.findByText('Brak danych pacjenta')).toBeVisible();
  });
});
