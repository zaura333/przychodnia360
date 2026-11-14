import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { createTestRouter } from '@/app/router';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { testData } from '@/shared/test/testData';

function mockAdmin() {
  const urls: URL[] = [];
  server.use(
    http.get('/api/v1/doctors', () => HttpResponse.json([testData.doctor()])),
    http.get('/api/v1/reception/appointments', ({ request }) => {
      urls.push(new URL(request.url));
      return HttpResponse.json([
        testData.staffAppointment({
          id: 'a-2',
          startAt: '2030-01-07T11:00:00Z',
          endAt: '2030-01-07T11:30:00Z',
          patientName: 'Piotr Zieliński',
        }),
        testData.staffAppointment({ id: 'a-1', status: 'CANCELLED' }),
      ]);
    }),
  );
  return urls;
}

describe('przegląd wizyt administratora', () => {
  it('is read-only: a table without any action buttons', async () => {
    mockAdmin();
    renderApp(createTestRouter(['/administracja/wizyty']), 'TENANT_ADMIN');
    const table = await screen.findByRole('table', { name: 'Wizyty placówki' });
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    expect(within(table).queryByRole('button')).not.toBeInTheDocument();
    expect(screen.getByText('Piotr Zieliński')).toBeVisible();
    expect(within(table).getByText('Odwołana')).toBeVisible();
  });

  it('sends filters as query parameters and resets to the first page', async () => {
    const urls = mockAdmin();
    renderApp(createTestRouter(['/administracja/wizyty']), 'TENANT_ADMIN');
    const user = userEvent.setup();
    await screen.findByRole('table');
    await user.selectOptions(screen.getByLabelText('Status'), 'CANCELLED');
    await user.selectOptions(screen.getByLabelText('Lekarz'), 'd-1');
    await waitFor(() => {
      const last = urls[urls.length - 1];
      expect(last?.searchParams.get('status')).toBe('CANCELLED');
      expect(last?.searchParams.get('employeeId')).toBe('d-1');
    });
  });

  it('rejects a range longer than 32 days without calling the API with it', async () => {
    const urls = mockAdmin();
    renderApp(createTestRouter(['/administracja/wizyty']), 'TENANT_ADMIN');
    await screen.findByRole('table');
    const to = screen.getByLabelText('Do');
    fireEvent.change(to, { target: { value: '2031-12-31' } });
    expect(await screen.findByText('Zakres może obejmować najwyżej 32 dni.')).toBeVisible();
    expect(urls.every((u) => u.searchParams.get('to') !== '2031-12-31')).toBe(true);
  });
});
