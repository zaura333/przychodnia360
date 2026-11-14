import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { createTestRouter } from '@/app/router';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { page, problemResponse, testData } from '@/shared/test/testData';

// 2030-01-07 to poniedziałek; 09:00Z to 10:00 w Warszawie (strefa testów to Los Angeles)
const SLOT_10 = { start: '2030-01-07T09:00:00Z', end: '2030-01-07T09:30:00Z' };
const SLOT_11 = { start: '2030-01-07T10:00:00Z', end: '2030-01-07T10:30:00Z' };

function mockBooking(
  options: { slots?: () => unknown[]; post?: () => Response | Promise<Response> } = {},
) {
  const posted: unknown[] = [];
  const slotCalls: URL[] = [];
  server.use(
    http.get('/api/v1/services', () =>
      HttpResponse.json(
        page([testData.service(), testData.service({ id: 's-2', name: 'EKG', price: 80 })]),
      ),
    ),
    http.get('/api/v1/doctors', () => HttpResponse.json([testData.doctor()])),
    http.get('/api/v1/employees/d-1/slots', ({ request }) => {
      slotCalls.push(new URL(request.url));
      return HttpResponse.json(options.slots ? options.slots() : [SLOT_10, SLOT_11]);
    }),
    http.post('/api/v1/appointments', async ({ request }) => {
      posted.push(await request.json());
      return options.post
        ? options.post()
        : HttpResponse.json(
            testData.staffAppointment({ startAt: SLOT_10.start, endAt: SLOT_10.end }),
            { status: 201 },
          );
    }),
  );
  return { posted, slotCalls };
}

const step = (n: number, title: string) => screen.findByText(`Krok ${String(n)} z 4: ${title}`);

async function chooseService(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('radio', { name: /Konsultacja/ }));
  await user.click(screen.getByRole('button', { name: 'Dalej' }));
}

describe('kreator rezerwacji', () => {
  it('books an appointment through all four steps without sending a patient id', async () => {
    const { posted } = mockBooking();
    renderApp(createTestRouter(['/pacjent/rezerwacja']), 'PATIENT');
    const user = userEvent.setup();
    await step(1, 'Usługa');
    await chooseService(user);
    await step(2, 'Lekarz');
    await user.click(screen.getByRole('radio', { name: /Jan Kowalski/ }));
    await user.click(screen.getByRole('button', { name: 'Dalej' }));
    await step(3, 'Termin');
    expect(screen.getByText(/Czas trwania wizyty:/)).toHaveTextContent('30 min');
    await user.click(await screen.findByRole('button', { name: /10:00–10:30/ }));
    expect(screen.getByText(/koniec o 10:30/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Dalej' }));
    await step(4, 'Potwierdzenie');
    expect(screen.getByText('150,00 zł')).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Potwierdzam rezerwację' }));
    expect(await screen.findByRole('heading', { name: 'Wizyta zarezerwowana' })).toBeVisible();
    expect(posted).toEqual([{ serviceId: 's-1', employeeId: 'd-1', startAt: SLOT_10.start }]);
    expect(
      within(screen.getByRole('main')).getByRole('link', { name: 'Moje wizyty' }),
    ).toHaveAttribute('href', '/pacjent/wizyty');
  });

  it('starts at the time step when both service and doctor come from the address', async () => {
    mockBooking();
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-1&employeeId=d-1']), 'PATIENT');
    await step(3, 'Termin');
    expect(await screen.findByRole('button', { name: /10:00–10:30/ })).toBeVisible();
  });

  it('starts at the doctor step when only the service is preselected', async () => {
    mockBooking();
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-2']), 'PATIENT');
    await step(2, 'Lekarz');
  });

  it('ignores unknown identifiers from the address and starts over', async () => {
    mockBooking();
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=obca&employeeId=obcy']), 'PATIENT');
    await step(1, 'Usługa');
    expect(await screen.findByRole('radio', { name: /Konsultacja/ })).not.toBeChecked();
  });

  it('keeps earlier choices when going back and clears the slot when the doctor changes', async () => {
    mockBooking();
    renderApp(createTestRouter(['/pacjent/rezerwacja']), 'PATIENT');
    const user = userEvent.setup();
    await chooseService(user);
    await user.click(await screen.findByRole('radio', { name: /Jan Kowalski/ }));
    await user.click(screen.getByRole('button', { name: 'Dalej' }));
    await user.click(await screen.findByRole('button', { name: /10:00–10:30/ }));
    await user.click(screen.getByRole('button', { name: 'Wstecz' }));
    expect(screen.getByRole('radio', { name: /Jan Kowalski/ })).toBeChecked();
    await user.click(screen.getByRole('radio', { name: /Jan Kowalski/ }));
    await user.click(screen.getByRole('button', { name: 'Wstecz' }));
    expect(screen.getByRole('radio', { name: /Konsultacja/ })).toBeChecked();
  });

  it('on 409 slot-taken returns to the time step with fresh slots and a clear message', async () => {
    let available = [SLOT_10, SLOT_11];
    mockBooking({
      slots: () => available,
      post: () => {
        available = [SLOT_11]; // ktoś zdążył zająć 10:00
        return problemResponse('slot-taken', 409);
      },
    });
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-1&employeeId=d-1']), 'PATIENT');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /10:00–10:30/ }));
    await user.click(screen.getByRole('button', { name: 'Dalej' }));
    await user.click(screen.getByRole('button', { name: 'Potwierdzam rezerwację' }));
    expect(
      await screen.findByText('Ten termin został właśnie zajęty. Wybierz inną godzinę.'),
    ).toBeVisible();
    await step(3, 'Termin');
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /10:00–10:30/ })).not.toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /11:00–11:30/ })).toBeVisible();
    // usługa i lekarz zostają, wybrany termin jest wyczyszczony
    expect(screen.getByRole('button', { name: 'Dalej' })).toBeDisabled();
  });

  it('distinguishes a patient overlap from a taken slot', async () => {
    mockBooking({ post: () => problemResponse('patient-overlap', 409) });
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-1&employeeId=d-1']), 'PATIENT');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /10:00–10:30/ }));
    await user.click(screen.getByRole('button', { name: 'Dalej' }));
    await user.click(screen.getByRole('button', { name: 'Potwierdzam rezerwację' }));
    expect(
      await screen.findByText('Masz już wizytę w tym czasie. Wybierz inną godzinę.'),
    ).toBeVisible();
  });

  it('on 422 refreshes the slots and asks for another time', async () => {
    const { slotCalls } = mockBooking({
      post: () => problemResponse('outside-working-hours', 422),
    });
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-1&employeeId=d-1']), 'PATIENT');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /10:00–10:30/ }));
    await user.click(screen.getByRole('button', { name: 'Dalej' }));
    const before = slotCalls.length;
    await user.click(screen.getByRole('button', { name: 'Potwierdzam rezerwację' }));
    expect(await screen.findByText(/nie jest już dostępny/)).toBeVisible();
    await waitFor(() => {
      expect(slotCalls.length).toBeGreaterThan(before);
    });
  });

  it('on 404 starts over from the first step', async () => {
    mockBooking({ post: () => problemResponse('not-found', 404) });
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-1&employeeId=d-1']), 'PATIENT');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /10:00–10:30/ }));
    await user.click(screen.getByRole('button', { name: 'Dalej' }));
    await user.click(screen.getByRole('button', { name: 'Potwierdzam rezerwację' }));
    await step(1, 'Usługa');
    expect(screen.getByText(/Zacznij od początku/)).toBeVisible();
  });

  it('disables the confirm button while the request is in flight', async () => {
    let release: () => void = () => undefined;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    mockBooking({
      post: async () => {
        await gate;
        return HttpResponse.json(testData.staffAppointment(), { status: 201 });
      },
    });
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-1&employeeId=d-1']), 'PATIENT');
    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: /10:00–10:30/ }));
    await user.click(screen.getByRole('button', { name: 'Dalej' }));
    await user.click(screen.getByRole('button', { name: 'Potwierdzam rezerwację' }));
    expect(await screen.findByRole('button', { name: 'Rezerwowanie…' })).toBeDisabled();
    release();
    expect(await screen.findByRole('heading', { name: 'Wizyta zarezerwowana' })).toBeVisible();
  });
});

describe('SlotPicker', () => {
  it('shows the empty message and sends a Monday-to-Sunday window', async () => {
    const { slotCalls } = mockBooking({ slots: () => [] });
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-1&employeeId=d-1']), 'PATIENT');
    expect(
      await screen.findByText('Brak wolnych terminów w tym tygodniu, sprawdź kolejny'),
    ).toBeVisible();
    const url = slotCalls[0];
    expect(url?.searchParams.get('serviceId')).toBe('s-1');
    expect(url?.searchParams.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(url?.searchParams.get('to')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('moves focus between slots with the arrow keys', async () => {
    mockBooking();
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-1&employeeId=d-1']), 'PATIENT');
    const user = userEvent.setup();
    const first = await screen.findByRole('button', { name: /10:00–10:30/ });
    first.focus();
    await user.keyboard('{ArrowRight}');
    expect(screen.getByRole('button', { name: /11:00–11:30/ })).toHaveFocus();
    await user.keyboard('{ArrowLeft}');
    expect(first).toHaveFocus();
  });

  it('requests the next week after navigation', async () => {
    const { slotCalls } = mockBooking({ slots: () => [] });
    renderApp(createTestRouter(['/pacjent/rezerwacja?serviceId=s-1&employeeId=d-1']), 'PATIENT');
    await screen.findByText('Brak wolnych terminów w tym tygodniu, sprawdź kolejny');
    await userEvent.click(screen.getByRole('button', { name: 'Następny tydzień' }));
    await waitFor(() => {
      expect(slotCalls).toHaveLength(2);
    });
    const [a, b] = slotCalls;
    expect(a?.searchParams.get('from')).not.toBe(b?.searchParams.get('from'));
  });
});

describe('moje wizyty', () => {
  it('splits upcoming and history, offers no cancel action and explains how to cancel', async () => {
    server.use(
      http.get('/api/v1/me/appointments', () =>
        HttpResponse.json([
          {
            ...testData.staffAppointment({
              id: 'a-future',
              startAt: '2030-01-07T09:00:00Z',
              endAt: '2030-01-07T09:30:00Z',
            }),
            patientId: undefined,
            patientName: undefined,
          },
          {
            ...testData.staffAppointment({
              id: 'a-old',
              startAt: '2020-01-07T09:00:00Z',
              endAt: '2020-01-07T09:30:00Z',
              status: 'COMPLETED',
              serviceName: 'Stara wizyta',
            }),
            patientId: undefined,
            patientName: undefined,
          },
          {
            ...testData.staffAppointment({
              id: 'a-cancelled',
              startAt: '2030-02-07T09:00:00Z',
              endAt: '2030-02-07T09:30:00Z',
              status: 'CANCELLED',
              serviceName: 'Odwołana wizyta',
            }),
            patientId: undefined,
            patientName: undefined,
          },
        ]),
      ),
    );
    renderApp(createTestRouter(['/pacjent/wizyty']), 'PATIENT');
    const user = userEvent.setup();
    expect(await screen.findByText('Zaplanowana')).toBeVisible();
    expect(screen.getByText('Aby odwołać wizytę, skontaktuj się z rejestracją.')).toBeVisible();
    expect(screen.queryByRole('button', { name: /Anuluj|Odwołaj/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/Stara wizyta/)).not.toBeInTheDocument();
    await user.click(screen.getByRole('tab', { name: 'Historia' }));
    expect(await screen.findByText(/Stara wizyta/)).toBeVisible();
    expect(screen.getByText(/Odwołana wizyta/)).toBeVisible();
    await user.selectOptions(screen.getByLabelText('Status'), 'CANCELLED');
    expect(screen.queryByText(/Stara wizyta/)).not.toBeInTheDocument();
    expect(screen.getByText(/Odwołana wizyta/)).toBeVisible();
  });

  it('shows an empty state without appointments', async () => {
    server.use(http.get('/api/v1/me/appointments', () => HttpResponse.json([])));
    renderApp(createTestRouter(['/pacjent/wizyty']), 'PATIENT');
    expect(await screen.findByText('Nie masz zaplanowanych wizyt.')).toBeVisible();
  });
});

describe('dzienna lista lekarza', () => {
  it('lists today appointments with patient names and statuses', async () => {
    server.use(
      http.get('/api/v1/me/schedule', () =>
        HttpResponse.json({
          intervals: [],
          appointments: [
            testData.staffAppointment({
              id: 'a-2',
              startAt: '2030-01-07T11:00:00Z',
              endAt: '2030-01-07T11:30:00Z',
              patientName: 'Piotr Zieliński',
            }),
            testData.staffAppointment({
              id: 'a-1',
              startAt: '2030-01-07T09:00:00Z',
              endAt: '2030-01-07T09:30:00Z',
              status: 'COMPLETED',
            }),
          ],
        }),
      ),
    );
    renderApp(createTestRouter(['/pracownik']), 'DOCTOR');
    expect(await screen.findByRole('heading', { name: 'Dzisiaj' })).toBeVisible();
    const items = await screen.findAllByRole('article');
    expect(items).toHaveLength(2);
    expect(within(items[0] as HTMLElement).getByText(/Anna Nowak/)).toBeVisible();
    expect(within(items[0] as HTMLElement).getByText('Wykonana')).toBeVisible();
    expect(within(items[1] as HTMLElement).getByText(/Piotr Zieliński/)).toBeVisible();
  });

  it('shows an empty state on a day without appointments', async () => {
    server.use(
      http.get('/api/v1/me/schedule', () => HttpResponse.json({ intervals: [], appointments: [] })),
    );
    renderApp(createTestRouter(['/pracownik']), 'DOCTOR');
    expect(await screen.findByText('Nie masz dziś wizyt')).toBeVisible();
  });
});
