import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { gridHours, toWorkingIntervals, weeklySummary } from './lib';
import { createTestRouter } from '@/app/router';
import { renderApp } from '@/shared/test/render';
import { server } from '@/shared/test/server';
import { testData } from '@/shared/test/testData';

describe('lib grafiku', () => {
  it('summarizes the weekly schedule in week order', () => {
    const weekly = [...testData.doctor().weekly].reverse();
    expect(weeklySummary(weekly as never)).toBe('Pn 08:00–14:00, Śr 10:00–18:00');
  });

  it('derives grid hours from working intervals in Warsaw time (winter and summer)', () => {
    // 09:00-13:30 w Warszawie: zimą UTC+1, latem UTC+2
    const winter = toWorkingIntervals([
      { start: '2026-01-12T08:00:00Z', end: '2026-01-12T12:30:00Z' },
    ]);
    expect(gridHours(winter)).toEqual({ startTime: '09:00', endTime: '14:00' });
    const summer = toWorkingIntervals([
      { start: '2026-07-13T07:00:00Z', end: '2026-07-13T11:30:00Z' },
    ]);
    expect(gridHours(summer)).toEqual({ startTime: '09:00', endTime: '14:00' });
  });

  it('falls back to 07:00-19:00 without intervals', () => {
    expect(gridHours([])).toEqual({ startTime: '07:00', endTime: '19:00' });
  });
});

describe('lista lekarzy', () => {
  it('shows doctors with a summary and loads the schedule on demand', async () => {
    let scheduleCalls = 0;
    server.use(
      http.get('/api/v1/doctors', () =>
        HttpResponse.json([
          testData.doctor(),
          testData.doctor({ id: 'd-2', lastName: 'Zielińska', weekly: [] }),
        ]),
      ),
      http.get('/api/v1/doctors/d-1/schedule', ({ request }) => {
        scheduleCalls += 1;
        const url = new URL(request.url);
        expect(url.searchParams.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
        return HttpResponse.json({ weekly: [], exceptions: [], intervals: [] });
      }),
    );
    renderApp(createTestRouter(['/pacjent/lekarze']), 'PATIENT');
    const card = (await screen.findByText('Jan Kowalski')).closest('article') as HTMLElement;
    expect(within(card).getByText('Pn 08:00–14:00, Śr 10:00–18:00')).toBeVisible();
    expect(within(card).getByRole('link', { name: 'Umów do tego lekarza' })).toHaveAttribute(
      'href',
      '/pacjent/rezerwacja?employeeId=d-1',
    );
    expect(screen.getByText('Brak grafiku')).toBeVisible();
    expect(scheduleCalls).toBe(0);
    await userEvent.click(within(card).getByRole('button', { name: /Pokaż grafik/ }));
    expect(await within(card).findByRole('grid')).toBeVisible();
    expect(scheduleCalls).toBe(1);
  });

  it('shows an empty state when the clinic has no doctors', async () => {
    server.use(http.get('/api/v1/doctors', () => HttpResponse.json([])));
    renderApp(createTestRouter(['/pacjent/lekarze']), 'PATIENT');
    expect(await screen.findByText('Brak lekarzy')).toBeVisible();
  });
});

describe('własny grafik lekarza', () => {
  it('renders the week grid with the doctor appointments', async () => {
    server.use(
      http.get('/api/v1/me/schedule', () =>
        HttpResponse.json({
          intervals: [],
          appointments: [
            testData.staffAppointment({
              startAt: '2030-01-07T09:00:00Z',
              endAt: '2030-01-07T09:30:00Z',
            }),
          ],
        }),
      ),
    );
    renderApp(createTestRouter(['/pracownik/grafik']), 'DOCTOR');
    expect(await screen.findByRole('heading', { name: 'Mój grafik' })).toBeVisible();
    expect(await screen.findByRole('group', { name: /Grafik tygodnia/ })).toBeVisible();
    expect(screen.getByRole('navigation', { name: 'Wybór tygodnia' })).toBeVisible();
  });
});
