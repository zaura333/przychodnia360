import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { entriesFromRows, rowsFromEntries, validateRows, type ScheduleRow } from './scheduleRows';
import { createTestRouter } from '@/app/router';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { problemResponse } from '@/shared/test/testData';

const row = (over: Partial<ScheduleRow> = {}): ScheduleRow => ({
  key: 'k1',
  dayOfWeek: 'MONDAY',
  startTime: '08:00',
  endTime: '12:00',
  validFrom: '2026-01-01',
  validTo: '',
  ...over,
});

describe('walidacja grafiku', () => {
  it('requires end after start and a valid period', () => {
    expect(validateRows([row({ endTime: '08:00' })]).k1).toMatch(/po jego początku/);
    expect(validateRows([row({ validTo: '2025-12-31' })]).k1).toMatch(/przed datą początku/);
    expect(validateRows([row({ startTime: '' })]).k1).toMatch(/Uzupełnij/);
    expect(validateRows([row()])).toEqual({});
  });

  it('detects overlapping shifts on the same weekday with overlapping periods', () => {
    const errors = validateRows([row(), row({ key: 'k2', startTime: '11:00', endTime: '15:00' })]);
    expect(errors.k2).toMatch(/nakładają się/);
  });

  it('allows touching shifts, other weekdays and disjoint periods', () => {
    expect(validateRows([row(), row({ key: 'k2', startTime: '12:00', endTime: '15:00' })])).toEqual(
      {},
    );
    expect(validateRows([row(), row({ key: 'k2', dayOfWeek: 'TUESDAY' })])).toEqual({});
    expect(
      validateRows([row({ validTo: '2026-06-30' }), row({ key: 'k2', validFrom: '2026-07-01' })]),
    ).toEqual({});
  });

  it('round-trips entries and sends validTo only when set', () => {
    const rows = rowsFromEntries([
      {
        id: 'w-1',
        dayOfWeek: 'MONDAY',
        startTime: '08:00:00',
        endTime: '12:00:00',
        validFrom: '2026-01-01',
      },
    ]);
    expect(rows[0]).toMatchObject({ startTime: '08:00', endTime: '12:00', validTo: '' });
    expect(entriesFromRows(rows)).toEqual([
      { dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '12:00', validFrom: '2026-01-01' },
    ]);
  });
});

const employee = (over: Record<string, unknown> = {}) => ({
  id: 'e-1',
  firstName: 'Jan',
  lastName: 'Kowalski',
  type: 'DOCTOR',
  specialization: 'Internista',
  active: true,
  userId: 'u-1',
  ...over,
});

describe('pracownicy (administrator)', () => {
  it('lists employees with roles and a schedule link only for doctors', async () => {
    server.use(
      http.get('/api/v1/admin/employees', () =>
        HttpResponse.json([
          employee(),
          employee({
            id: 'e-2',
            firstName: 'Ewa',
            lastName: 'Rejestr',
            type: 'RECEPTIONIST',
            specialization: null,
          }),
        ]),
      ),
    );
    renderApp(createTestRouter(['/administracja/pracownicy']), 'TENANT_ADMIN');
    const table = await screen.findByRole('table', { name: 'Pracownicy placówki' });
    expect(within(table).getByText('Jan Kowalski')).toBeVisible();
    expect(within(table).getByText('Rejestrator')).toBeVisible();
    expect(
      within(table).getByRole('link', { name: 'Grafik pracownika Jan Kowalski' }),
    ).toHaveAttribute('href', '/administracja/pracownicy/e-1/grafik');
    expect(within(table).queryByRole('link', { name: /Ewa Rejestr/ })).not.toBeInTheDocument();
  });

  it('creates an employee without a password field and explains the set-password link', async () => {
    let body: unknown;
    server.use(
      http.get('/api/v1/admin/employees', () => HttpResponse.json([])),
      http.post('/api/v1/admin/employees', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(employee(), { status: 201 });
      }),
    );
    renderApp(createTestRouter(['/administracja/pracownicy']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dodaj pracownika' }));
    const dialog = await screen.findByRole('dialog', { name: 'Nowy pracownik' });
    const d = within(dialog);
    expect(d.queryByLabelText(/hasło/i)).not.toBeInTheDocument();
    expect(d.queryByRole('option', { name: 'Personel medyczny' })).not.toBeInTheDocument();
    await user.type(d.getByLabelText('Imię'), 'Jan');
    await user.type(d.getByLabelText('Nazwisko'), 'Kowalski');
    await user.type(d.getByLabelText('Adres e-mail'), 'jan@klinika-a.example.test');
    await user.type(d.getByLabelText('Specjalizacja'), 'Internista');
    await user.click(d.getByRole('button', { name: 'Zapisz' }));
    await waitFor(() => {
      expect(body).toEqual({
        firstName: 'Jan',
        lastName: 'Kowalski',
        email: 'jan@klinika-a.example.test',
        type: 'DOCTOR',
        specialization: 'Internista',
      });
    });
  });

  it('reports a taken e-mail on the field', async () => {
    server.use(
      http.get('/api/v1/admin/employees', () => HttpResponse.json([])),
      http.post('/api/v1/admin/employees', () => problemResponse('email-taken', 409)),
    );
    renderApp(createTestRouter(['/administracja/pracownicy']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dodaj pracownika' }));
    const d = within(await screen.findByRole('dialog', { name: 'Nowy pracownik' }));
    await user.type(d.getByLabelText('Imię'), 'Jan');
    await user.type(d.getByLabelText('Nazwisko'), 'Kowalski');
    await user.type(d.getByLabelText('Adres e-mail'), 'zajety@example.test');
    await user.click(d.getByRole('button', { name: 'Zapisz' }));
    expect(await d.findByText('Konto z tym adresem już istnieje')).toBeVisible();
  });

  it('explains why an employee with future appointments cannot be deactivated', async () => {
    server.use(
      http.get('/api/v1/admin/employees', () => HttpResponse.json([employee()])),
      http.post('/api/v1/admin/employees/e-1/deactivate', () =>
        problemResponse('employee-has-future-appointments', 409),
      ),
    );
    renderApp(createTestRouter(['/administracja/pracownicy']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(
      await screen.findByRole('button', { name: 'Dezaktywuj pracownika Jan Kowalski' }),
    );
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/Konto utraci dostęp natychmiast/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Dezaktywuj pracownika' }));
    expect(await screen.findByText('Pracownik ma przyszłe wizyty')).toBeVisible();
  });
});

const scheduleResponse = (over: Record<string, unknown> = {}) => ({
  weekly: [
    {
      id: 'w-1',
      dayOfWeek: 'MONDAY',
      startTime: '08:00:00',
      endTime: '12:00:00',
      validFrom: '2026-01-01',
    },
  ],
  exceptions: [],
  intervals: [],
  ...over,
});

function mockEmployeeSchedule(response = scheduleResponse()) {
  server.use(
    http.get('/api/v1/admin/employees', () => HttpResponse.json([employee()])),
    http.get('/api/v1/admin/employees/e-1/schedule', () => HttpResponse.json(response)),
  );
}

describe('edytor grafiku', () => {
  it('disables saving until a valid change and replaces the whole schedule', async () => {
    mockEmployeeSchedule();
    let body: unknown;
    server.use(
      http.put('/api/v1/admin/employees/e-1/schedule', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(scheduleResponse().weekly);
      }),
    );
    renderApp(createTestRouter(['/administracja/pracownicy/e-1/grafik']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    const save = await screen.findByRole('button', { name: 'Zapisz grafik' });
    expect(save).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Dodaj dyżur' }));
    // Nowy dyżur w poniedziałek nakłada się na istniejący, więc zapis jest zablokowany
    expect(await screen.findByText('Dyżury w tym samym dniu nakładają się')).toBeVisible();
    expect(save).toBeDisabled();
    await user.selectOptions(screen.getByLabelText('Dzień (dyżur 2)'), 'TUESDAY');
    expect(save).toBeEnabled();
    const end = screen.getByLabelText('Do (dyżur 2)');
    await user.clear(end);
    await user.type(end, '07:00');
    expect(await screen.findByText('Koniec dyżuru musi być po jego początku')).toBeVisible();
    expect(save).toBeDisabled();
    await user.clear(end);
    await user.type(end, '16:00');
    expect(save).toBeEnabled();
    await user.click(save);
    await waitFor(() => {
      expect(body).toMatchObject({
        entries: [
          { dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '12:00' },
          { dayOfWeek: 'TUESDAY', startTime: '08:00', endTime: '16:00' },
        ],
      });
    });
  });

  it('flags overlapping shifts live', async () => {
    mockEmployeeSchedule();
    renderApp(createTestRouter(['/administracja/pracownicy/e-1/grafik']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dodaj dyżur' }));
    const from = screen.getByLabelText('Od (dyżur 2)');
    await user.clear(from);
    await user.type(from, '10:00');
    expect(await screen.findByText('Dyżury w tym samym dniu nakładają się')).toBeVisible();
  });

  it('shows the backend overlap message at the top of the editor', async () => {
    mockEmployeeSchedule();
    server.use(
      http.put('/api/v1/admin/employees/e-1/schedule', () =>
        problemResponse('schedule-overlap', 409),
      ),
    );
    renderApp(createTestRouter(['/administracja/pracownicy/e-1/grafik']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dodaj dyżur' }));
    await user.selectOptions(screen.getByLabelText('Dzień (dyżur 2)'), 'FRIDAY');
    await user.click(screen.getByRole('button', { name: 'Zapisz grafik' }));
    expect(await screen.findByText(/Dyżury nakładają się w tym samym dniu tygodnia/)).toBeVisible();
  });

  it('warns about unsaved changes when leaving the page', async () => {
    mockEmployeeSchedule();
    renderApp(createTestRouter(['/administracja/pracownicy/e-1/grafik']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Dodaj dyżur' }));
    await user.click(screen.getByRole('link', { name: 'Wróć do pracowników' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/niezapisane zmiany/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Zostań' }));
    expect(screen.getByRole('heading', { level: 1, name: /Grafik/ })).toBeVisible();
  });
});

describe('wyjątki grafiku', () => {
  it('adds a leave day, an extra shift with hours and deletes an exception', async () => {
    mockEmployeeSchedule(
      scheduleResponse({
        exceptions: [{ id: 'x-1', date: '2030-01-07', type: 'ABSENCE' }],
      }),
    );
    const posted: unknown[] = [];
    let deleted = '';
    server.use(
      http.post('/api/v1/admin/employees/e-1/schedule-exceptions', async ({ request }) => {
        posted.push(await request.json());
        return HttpResponse.json(
          { id: 'x-2', date: '2030-01-08', type: 'ABSENCE' },
          { status: 201 },
        );
      }),
      http.delete('/api/v1/admin/employees/e-1/schedule-exceptions/:id', ({ params }) => {
        deleted = String(params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp(createTestRouter(['/administracja/pracownicy/e-1/grafik']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    expect(await screen.findByText(/07\.01\.2030 · Urlop/)).toBeVisible();
    const form = within(screen.getByRole('form', { name: 'Nowy wyjątek grafiku' }));
    await user.click(form.getByRole('button', { name: 'Dodaj wyjątek' }));
    await waitFor(() => {
      expect(posted).toHaveLength(1);
    });
    expect(posted[0]).toMatchObject({ type: 'ABSENCE' });
    expect(posted[0]).not.toHaveProperty('startTime');
    await user.selectOptions(form.getByLabelText('Rodzaj'), 'EXTRA_SHIFT');
    await user.click(form.getByRole('button', { name: 'Dodaj wyjątek' }));
    expect(await form.findByText('Podaj godzinę początku')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Usuń wyjątek z dnia 07.01.2030' }));
    await waitFor(() => {
      expect(deleted).toBe('x-1');
    });
  });
});
