import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { createTestRouter } from '@/app/router';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { page, problemResponse, testData } from '@/shared/test/testData';

const categories = [
  { id: 'c-1', name: 'Konsultacje' },
  { id: 'c-2', name: 'Badania' },
];
const service = (id: string, name: string, price = 150) =>
  testData.service({ id, name, price, categoryId: 'c-1' });

function mockCatalog(
  items = [service('s-1', 'Konsultacja internistyczna'), service('s-2', 'EKG', 80)],
) {
  const seen: URL[] = [];
  server.use(
    http.get('/api/v1/service-categories', () => HttpResponse.json(categories)),
    http.get('/api/v1/services', ({ request }) => {
      seen.push(new URL(request.url));
      return HttpResponse.json(page(items));
    }),
  );
  return seen;
}

describe('lista usług', () => {
  it('shows cards with price, duration and a book button for patients', async () => {
    mockCatalog();
    renderApp(createTestRouter(['/pacjent/uslugi']), 'PATIENT');
    const card = (await screen.findByText('Konsultacja internistyczna')).closest('article');
    expect(card).not.toBeNull();
    const c = within(card as HTMLElement);
    expect(c.getByText(/150,00/)).toBeVisible();
    expect(c.getByText(/30 min/)).toBeVisible();
    expect(c.getByRole('link', { name: 'Umów' })).toHaveAttribute(
      'href',
      '/pacjent/rezerwacja?serviceId=s-1',
    );
  });

  it('has no book button for staff roles', async () => {
    mockCatalog();
    renderApp(createTestRouter(['/pracownik/uslugi']), 'RECEPTIONIST');
    await screen.findByText('EKG');
    expect(screen.queryByRole('link', { name: 'Umów' })).not.toBeInTheDocument();
  });

  it('keeps filters in the address and sends them to the API', async () => {
    const seen = mockCatalog();
    const router = createTestRouter(['/pacjent/uslugi']);
    renderApp(router, 'PATIENT');
    await screen.findByText('EKG');
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText('Kategoria'), 'c-2');
    await user.type(screen.getByLabelText('Cena od (zł)'), '50');
    await user.click(screen.getByRole('button', { name: 'Filtruj' }));
    await waitFor(() => {
      expect(router.state.location.search).toContain('categoryId=c-2');
    });
    expect(router.state.location.search).toContain('minPrice=50');
    await waitFor(() => {
      const last = seen[seen.length - 1];
      expect(last?.searchParams.get('categoryId')).toBe('c-2');
      expect(last?.searchParams.get('minPrice')).toBe('50');
    });
  });

  it('shows an empty state and an error with retry', async () => {
    server.use(
      http.get('/api/v1/service-categories', () => HttpResponse.json(categories)),
      http.get('/api/v1/services', () => HttpResponse.json(page([]))),
    );
    renderApp(createTestRouter(['/pacjent/uslugi']), 'PATIENT');
    expect(await screen.findByText('Brak usług spełniających kryteria')).toBeVisible();
  });

  it('shows the error state when the API fails', async () => {
    server.use(
      http.get('/api/v1/service-categories', () => HttpResponse.json(categories)),
      http.get('/api/v1/services', () => problemResponse('internal', 500)),
    );
    renderApp(createTestRouter(['/pacjent/uslugi']), 'PATIENT');
    expect(
      await screen.findByRole('button', { name: 'Spróbuj ponownie' }, { timeout: 8000 }),
    ).toBeVisible();
  }, 12000);
});

describe('szczegóły usługi', () => {
  it('renders the service and a book link', async () => {
    server.use(
      http.get('/api/v1/service-categories', () => HttpResponse.json(categories)),
      http.get('/api/v1/services/s-1', () =>
        HttpResponse.json(service('s-1', 'Konsultacja internistyczna')),
      ),
    );
    renderApp(createTestRouter(['/pacjent/uslugi/s-1']), 'PATIENT');
    expect(
      await screen.findByRole('heading', { name: 'Konsultacja internistyczna' }),
    ).toBeVisible();
    expect(
      within(screen.getByRole('main')).getByRole('link', { name: 'Umów wizytę' }),
    ).toHaveAttribute('href', '/pacjent/rezerwacja?serviceId=s-1');
  });

  it('shows the 404 page for a service from another tenant', async () => {
    server.use(
      http.get('/api/v1/service-categories', () => HttpResponse.json(categories)),
      http.get('/api/v1/services/obca', () => problemResponse('not-found', 404)),
    );
    renderApp(createTestRouter(['/pacjent/uslugi/obca']), 'PATIENT');
    expect(await screen.findByRole('heading', { name: 'Nie znaleziono strony' })).toBeVisible();
  });
});

describe('administracja usługami', () => {
  it('creates a service with the price sent in PLN', async () => {
    mockCatalog([]);
    let body: Record<string, unknown> = {};
    server.use(
      http.post('/api/v1/admin/services', async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json(service('s-9', 'Szczepienie', 99.5), { status: 201 });
      }),
    );
    renderApp(createTestRouter(['/administracja/uslugi']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dodaj usługę' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nowa usługa' });
    const d = within(dialog);
    await user.type(d.getByLabelText('Nazwa'), 'Szczepienie');
    await user.selectOptions(d.getByLabelText('Kategoria'), 'c-1');
    await user.type(d.getByLabelText('Cena'), '99,5');
    await user.clear(d.getByLabelText('Czas trwania (minuty)'));
    await user.type(d.getByLabelText('Czas trwania (minuty)'), '20');
    await user.click(d.getByRole('button', { name: 'Zapisz' }));
    await waitFor(() => {
      expect(body).toMatchObject({
        name: 'Szczepienie',
        categoryId: 'c-1',
        price: 99.5,
        durationMinutes: 20,
        performerType: 'DOCTOR',
      });
    });
  });

  it('validates the duration range before sending', async () => {
    mockCatalog([]);
    renderApp(createTestRouter(['/administracja/uslugi']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dodaj usługę' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nowa usługa' });
    const d = within(dialog);
    await user.clear(d.getByLabelText('Czas trwania (minuty)'));
    await user.type(d.getByLabelText('Czas trwania (minuty)'), '3');
    await user.click(d.getByRole('button', { name: 'Zapisz' }));
    expect(await d.findByText('Czas trwania: od 5 minut')).toBeVisible();
  });

  it('deactivates a service after confirmation', async () => {
    mockCatalog([service('s-1', 'EKG', 80)]);
    let deleted = '';
    server.use(
      http.delete('/api/v1/admin/services/:id', ({ params }) => {
        deleted = String(params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp(createTestRouter(['/administracja/uslugi']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dezaktywuj usługę EKG' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/wcześniejsze wizyty pozostaną w historii/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Dezaktywuj usługę' }));
    await waitFor(() => {
      expect(deleted).toBe('s-1');
    });
  });

  it('shows the backend message when a category is in use', async () => {
    mockCatalog([service('s-1', 'EKG', 80)]);
    server.use(
      http.delete('/api/v1/admin/service-categories/:id', () =>
        problemResponse('category-in-use', 409),
      ),
    );
    renderApp(createTestRouter(['/administracja/uslugi']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Usuń kategorię Konsultacje' }));
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Usuń kategorię' }));
    expect(await screen.findByText('Kategoria jest używana')).toBeVisible();
  });

  it('offers a reload on an optimistic locking conflict', async () => {
    mockCatalog([service('s-1', 'EKG', 80)]);
    server.use(
      http.put('/api/v1/admin/services/s-1', () => problemResponse('concurrent-update', 409)),
    );
    renderApp(createTestRouter(['/administracja/uslugi']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Edytuj usługę EKG' }));
    const dialog = await screen.findByRole('dialog', { name: 'Edycja usługi' });
    await user.click(within(dialog).getByRole('button', { name: 'Zapisz' }));
    expect(await within(dialog).findByText('Ktoś zmienił te dane')).toBeVisible();
    expect(within(dialog).getByRole('button', { name: 'Przeładuj dane' })).toBeVisible();
  });
});
