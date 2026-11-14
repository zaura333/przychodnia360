import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { createTestRouter } from '@/app/router';
import { combine, toIsoString, today } from '@/shared/lib/datetime';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { page, problemResponse, testData } from '@/shared/test/testData';

const day = today();
const at = (hhmm: string) => toIsoString(combine(day, hhmm));

const interval = { start: at('08:00'), end: at('12:00') };
const patient = {
  id: 'p-1',
  firstName: 'Anna',
  lastName: 'Nowak',
  birthDate: '1990-05-17',
  contactEmail: 'anna@example.test',
  hasAccount: false,
};

function appointment(overrides: Record<string, unknown> = {}) {
  return testData.staffAppointment({
    id: 'a-1',
    startAt: at('09:00'),
    endAt: at('09:30'),
    ...overrides,
  });
}

interface Mocks {
  appointments?: () => unknown[];
  doctors?: unknown[];
}

function mockSchedule(mocks: Mocks = {}) {
  const calls = { appointments: 0, slots: 0 };
  server.use(
    http.get('/api/v1/doctors', () =>
      HttpResponse.json(
        mocks.doctors ?? [
          testData.doctor(),
          testData.doctor({ id: 'd-2', firstName: 'Ewa', lastName: 'Zielińska' }),
        ],
      ),
    ),
    http.get('/api/v1/doctors/:id/schedule', () =>
      HttpResponse.json({ weekly: [], exceptions: [], intervals: [interval] }),
    ),
    http.get('/api/v1/reception/appointments', () => {
      calls.appointments += 1;
      return HttpResponse.json(mocks.appointments ? mocks.appointments() : [appointment()]);
    }),
    http.get('/api/v1/services', () => HttpResponse.json(page([testData.service()]))),
    http.post('/api/v1/reception/patients/search', () =>
      HttpResponse.json(page([patient], { size: 20 })),
    ),
    http.get('/api/v1/employees/:id/slots', () => {
      calls.slots += 1;
      return HttpResponse.json([
        { start: at('10:00'), end: at('10:30') },
        { start: at('10:30'), end: at('11:00') },
      ]);
    }),
  );
  return calls;
}

const cell = (name: string) => screen.findByRole('gridcell', { name });

describe('terminarz rejestratora', () => {
  it('shows doctor columns with working hours, booked and free slots', async () => {
    mockSchedule();
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    expect(await cell('Jan Kowalski, 08:00, wolny')).toBeVisible();
    expect(await cell('Jan Kowalski, 09:00, zajęty, Konsultacja, Anna Nowak')).toBeVisible();
    expect(await cell('Ewa Zielińska, 09:00, wolny')).toBeVisible();
    expect(screen.getByRole('columnheader', { name: /Jan Kowalski/ })).toBeVisible();
  });

  it('redirects the receptionist home page to the schedule', async () => {
    mockSchedule();
    const router = createTestRouter(['/pracownik']);
    renderApp(router, 'RECEPTIONIST');
    await cell('Jan Kowalski, 08:00, wolny');
    expect(router.state.location.pathname).toBe('/pracownik/terminarz');
  });

  it('books a patient from a free slot and sends the patient id', async () => {
    mockSchedule();
    const posted: unknown[] = [];
    server.use(
      http.post('/api/v1/appointments', async ({ request }) => {
        posted.push(await request.json());
        return HttpResponse.json(
          appointment({ id: 'a-2', startAt: at('10:00'), endAt: at('10:30') }),
          {
            status: 201,
          },
        );
      }),
    );
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    const user = userEvent.setup();
    await user.click(await cell('Jan Kowalski, 10:00, wolny'));
    const dialog = await screen.findByRole('dialog', { name: 'Umów wizytę dla pacjenta' });
    const d = within(dialog);
    expect(d.getByRole('button', { name: 'Umów wizytę' })).toBeDisabled();
    await user.selectOptions(await d.findByLabelText('Usługa'), 's-1');
    await user.type(d.getByLabelText('Szukaj pacjenta'), 'Nowak');
    await user.click(await d.findByRole('button', { name: 'Wybierz pacjenta: Anna Nowak' }));
    await user.click(d.getByRole('button', { name: 'Umów wizytę' }));
    await waitFor(() => {
      expect(posted).toEqual([
        { serviceId: 's-1', employeeId: 'd-1', patientId: 'p-1', startAt: at('10:00') },
      ]);
    });
  });

  it('keeps the dialog open with the patient and service after a 409 and refreshes the grid', async () => {
    const calls = mockSchedule();
    server.use(http.post('/api/v1/appointments', () => problemResponse('slot-taken', 409)));
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    const user = userEvent.setup();
    await user.click(await cell('Jan Kowalski, 10:00, wolny'));
    const dialog = await screen.findByRole('dialog', { name: 'Umów wizytę dla pacjenta' });
    const d = within(dialog);
    await user.selectOptions(await d.findByLabelText('Usługa'), 's-1');
    await user.type(d.getByLabelText('Szukaj pacjenta'), 'Nowak');
    await user.click(await d.findByRole('button', { name: 'Wybierz pacjenta: Anna Nowak' }));
    const before = calls.appointments;
    await user.click(d.getByRole('button', { name: 'Umów wizytę' }));
    expect(
      await d.findByText('Ten termin został właśnie zajęty. Wybierz inną godzinę.'),
    ).toBeVisible();
    expect(d.getByText('Anna Nowak')).toBeVisible();
    expect(d.getByLabelText('Usługa')).toHaveValue('s-1');
    await waitFor(() => {
      expect(calls.appointments).toBeGreaterThan(before);
    });
  });

  it('creates a new patient inside the booking dialog and selects them', async () => {
    mockSchedule();
    server.use(
      http.post('/api/v1/reception/patients', () =>
        HttpResponse.json(
          { ...patient, id: 'p-9', firstName: 'Jan', lastName: 'Nowy' },
          { status: 201 },
        ),
      ),
    );
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    const user = userEvent.setup();
    await user.click(await cell('Jan Kowalski, 10:00, wolny'));
    const dialog = await screen.findByRole('dialog', { name: 'Umów wizytę dla pacjenta' });
    const d = within(dialog);
    await user.click(d.getByRole('button', { name: 'Nowy pacjent' }));
    await user.type(d.getByLabelText('Imię'), 'Jan');
    await user.type(d.getByLabelText('Nazwisko'), 'Nowy');
    await user.type(d.getByLabelText('Data urodzenia'), '1985-01-02');
    await user.click(d.getByRole('button', { name: 'Zapisz pacjenta' }));
    expect(await d.findByText('Jan Nowy')).toBeVisible();
  });

  it('cancels an appointment after confirmation and frees the slot', async () => {
    let list = [appointment()];
    mockSchedule({ appointments: () => list });
    let cancelled = '';
    server.use(
      http.post('/api/v1/appointments/:id/cancel', ({ params }) => {
        cancelled = String(params.id);
        list = [appointment({ status: 'CANCELLED' })];
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    const user = userEvent.setup();
    await user.click(await cell('Jan Kowalski, 09:00, zajęty, Konsultacja, Anna Nowak'));
    const panel = await screen.findByRole('region', { name: 'Szczegóły wizyty' });
    await user.click(within(panel).getByRole('button', { name: 'Odwołaj' }));
    const dialog = await screen.findByRole('alertdialog');
    expect(within(dialog).getByText(/Termin wróci do puli wolnych terminów/)).toBeVisible();
    await user.click(within(dialog).getByRole('button', { name: 'Odwołaj wizytę' }));
    await waitFor(() => {
      expect(cancelled).toBe('a-1');
    });
    expect(await cell('Jan Kowalski, 09:00, wolny')).toBeVisible();
  });

  it('reschedules to a chosen slot and stays open on a collision', async () => {
    mockSchedule();
    let attempts = 0;
    server.use(
      http.post('/api/v1/appointments/:id/reschedule', () => {
        attempts += 1;
        return attempts === 1
          ? problemResponse('slot-taken', 409)
          : HttpResponse.json(appointment({ startAt: at('10:30'), endAt: at('11:00') }));
      }),
    );
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    const user = userEvent.setup();
    await user.click(await cell('Jan Kowalski, 09:00, zajęty, Konsultacja, Anna Nowak'));
    const panel = await screen.findByRole('region', { name: 'Szczegóły wizyty' });
    await user.click(within(panel).getByRole('button', { name: 'Przesuń' }));
    const dialog = await screen.findByRole('dialog', { name: 'Przesuń wizytę' });
    const d = within(dialog);
    await user.click(await d.findByRole('button', { name: /10:00–10:30/ }));
    await user.click(d.getByRole('button', { name: 'Przesuń wizytę' }));
    expect(await d.findByText(/Ten termin został właśnie zajęty/)).toBeVisible();
    expect(screen.getByRole('dialog', { name: 'Przesuń wizytę' })).toBeVisible();
    await user.click(await d.findByRole('button', { name: /10:30–11:00/ }));
    await user.click(d.getByRole('button', { name: 'Przesuń wizytę' }));
    await waitFor(() => {
      expect(attempts).toBe(2);
    });
    await waitFor(() => {
      expect(screen.queryByRole('dialog', { name: 'Przesuń wizytę' })).not.toBeInTheDocument();
    });
  });

  it('hides a doctor column with the visibility checkboxes', async () => {
    mockSchedule();
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    await cell('Ewa Zielińska, 09:00, wolny');
    await userEvent.click(screen.getByRole('checkbox', { name: 'Ewa Zielińska' }));
    await waitFor(() => {
      expect(screen.queryByRole('gridcell', { name: /Ewa Zielińska/ })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('gridcell', { name: /Jan Kowalski, 09:00/ })).toBeVisible();
  });

  it('lists cancelled appointments only when asked', async () => {
    mockSchedule({
      appointments: () => [appointment({ status: 'CANCELLED', serviceName: 'Odwołana usługa' })],
    });
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    await cell('Jan Kowalski, 09:00, wolny');
    expect(screen.queryByText(/Odwołana usługa/)).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Pokaż odwołane' }));
    expect(await screen.findByText(/Odwołana usługa.*\(odwołana\)/)).toBeVisible();
  });

  it('opens the booking dialog with the keyboard (arrows and Enter)', async () => {
    mockSchedule();
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    const user = userEvent.setup();
    const first = await cell('Jan Kowalski, 08:00, wolny');
    first.focus();
    await user.keyboard('{ArrowDown}');
    expect(screen.getByRole('gridcell', { name: 'Jan Kowalski, 08:15, wolny' })).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(await screen.findByRole('dialog', { name: 'Umów wizytę dla pacjenta' })).toBeVisible();
  });

  it('keeps the chosen day when the request fails and retries', async () => {
    mockSchedule();
    let fail = true;
    server.use(
      http.get('/api/v1/reception/appointments', () => {
        if (fail) return problemResponse('internal', 500);
        return HttpResponse.json([]);
      }),
    );
    renderApp(createTestRouter(['/pracownik/terminarz']), 'RECEPTIONIST');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Następny dzień' }));
    const retry = await screen.findByRole(
      'button',
      { name: 'Spróbuj ponownie' },
      { timeout: 10000 },
    );
    fail = false;
    await user.click(retry);
    expect(await screen.findByRole('grid')).toBeVisible();
  }, 20000);
});
