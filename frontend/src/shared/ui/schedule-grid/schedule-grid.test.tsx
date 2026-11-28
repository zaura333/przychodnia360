import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ScheduleGrid, VIRTUALIZE_ABOVE } from './schedule-grid';
import type { ScheduleGridProps } from './types';
import { combine, type Instant, type LocalDate } from '@/shared/lib/datetime';
import { expectNoA11yViolations } from '@/shared/test/a11y';
import { generateScheduleData } from '@/shared/test/scheduleData';

const day = '2026-06-10' as LocalDate;
const at = (hhmm: string): Instant => combine(day, hhmm);

function baseProps(overrides: Partial<ScheduleGridProps> = {}): ScheduleGridProps {
  return {
    columns: [
      { id: 'a', label: 'dr Anna Nowak' },
      { id: 'b', label: 'dr Jan Kowalski' },
    ],
    day,
    slotMinutes: 15,
    startTime: '07:00',
    endTime: '19:00',
    workingIntervals: {
      a: [{ start: at('08:00'), end: at('16:00') }],
      b: [{ start: at('08:00'), end: at('12:00') }],
    },
    appointments: {
      a: [
        { id: 'a1', start: at('09:00'), end: at('09:45'), label: 'Konsultacja', status: 'booked' },
      ],
    },
    onSlotSelect: vi.fn(),
    onAppointmentSelect: vi.fn(),
    ...overrides,
  };
}

const cell = (label: string) => screen.getByRole('gridcell', { name: label });

describe('ScheduleGrid: układ', () => {
  it('has 48 rows for 07:00-19:00 at 15 minutes and labels cell states', () => {
    render(<ScheduleGrid {...baseProps()} />);
    expect(screen.getAllByRole('rowheader')).toHaveLength(48);
    expect(cell('dr Anna Nowak, 08:00, wolny')).toBeInTheDocument();
    expect(cell('dr Anna Nowak, 09:00, zajęty, Konsultacja')).toBeInTheDocument();
    expect(cell('dr Anna Nowak, 09:30, zajęty, Konsultacja')).toBeInTheDocument();
    expect(cell('dr Anna Nowak, 09:45, wolny')).toBeInTheDocument();
    expect(cell('dr Jan Kowalski, 13:00, poza dyżurem')).toHaveAttribute('aria-disabled', 'true');
  });

  it('places the appointment block by duration (grid-row span) in the grid', () => {
    const { container } = render(<ScheduleGrid {...baseProps()} />);
    const block = container.querySelector<HTMLElement>('[data-appt="a1"]');
    expect(block).not.toBeNull();
    // 09:00 to wiersz 8 (od 07:00 co 15 min), +2 na nagłówek; 45 min = 3 sloty
    // własności zamiast tekstu atrybutu: jsdom 31 serializuje grid-row + grid-column jako skrót grid-area
    expect(block?.style.gridRow).toBe('10 / span 3');
    expect(block?.style.gridColumn).toBe('2');
  });

  it('cancelled appointments do not occupy the slot', () => {
    render(
      <ScheduleGrid
        {...baseProps({
          appointments: {
            a: [
              {
                id: 'x',
                start: at('09:00'),
                end: at('09:30'),
                label: 'Odwołana',
                status: 'cancelled',
              },
            ],
          },
        })}
      />,
    );
    expect(cell('dr Anna Nowak, 09:00, wolny')).toBeInTheDocument();
  });

  it('supports the one-doctor-seven-days mode through per-column days', () => {
    const next = '2026-06-11' as LocalDate;
    render(
      <ScheduleGrid
        {...baseProps({
          columns: [
            { id: 'd1', label: 'Śr 10.06' },
            { id: 'd2', label: 'Czw 11.06', day: next },
          ],
          workingIntervals: {
            d1: [],
            d2: [{ start: combine(next, '08:00'), end: combine(next, '10:00') }],
          },
          appointments: {},
        })}
      />,
    );
    expect(cell('Czw 11.06, 08:00, wolny')).toBeInTheDocument();
    expect(cell('Śr 10.06, 08:00, poza dyżurem')).toBeInTheDocument();
  });

  it('shows a skeleton while loading', () => {
    render(<ScheduleGrid {...baseProps({ loading: true })} />);
    expect(screen.getByRole('status', { name: 'Ładowanie terminarza' })).toBeInTheDocument();
    expect(screen.queryByRole('grid')).not.toBeInTheDocument();
  });

  it('virtualizes columns above the threshold', () => {
    const data = generateScheduleData({ day, doctors: 30 });
    render(
      <ScheduleGrid
        {...baseProps({ ...data, onSlotSelect: vi.fn(), onAppointmentSelect: vi.fn() })}
      />,
    );
    const headers = screen.getAllByRole('columnheader');
    expect(headers.length).toBeLessThan(30);
    expect(headers.length).toBeGreaterThanOrEqual(VIRTUALIZE_ABOVE);
    expect(screen.getByRole('grid')).toHaveAttribute('aria-colcount', '31');
  });

  it('does not virtualize up to the threshold', () => {
    const data = generateScheduleData({ day, doctors: VIRTUALIZE_ABOVE });
    render(<ScheduleGrid {...baseProps(data)} />);
    expect(screen.getAllByRole('columnheader')).toHaveLength(VIRTUALIZE_ABOVE + 1); // + nagłówek godzin
  });
});

describe('ScheduleGrid: interakcja', () => {
  it('free slot click reports column and instant; non-working and booked behave correctly', async () => {
    const props = baseProps();
    render(<ScheduleGrid {...props} />);

    await userEvent.click(cell('dr Anna Nowak, 08:30, wolny'));
    expect(props.onSlotSelect).toHaveBeenCalledWith({ columnId: 'a', start: at('08:30') });

    await userEvent.click(cell('dr Jan Kowalski, 13:00, poza dyżurem'));
    expect(props.onSlotSelect).toHaveBeenCalledOnce();

    await userEvent.click(cell('dr Anna Nowak, 09:15, zajęty, Konsultacja'));
    expect(props.onAppointmentSelect).toHaveBeenCalledWith('a1');
  });

  it('roving tabindex: exactly one cell is in the tab order', () => {
    render(<ScheduleGrid {...baseProps()} />);
    const inTabOrder = screen.getAllByRole('gridcell').filter((c) => c.tabIndex === 0);
    expect(inTabOrder).toHaveLength(1);
  });

  it('keyboard: arrows, Home/End, PageDown move focus; Enter activates; Escape leaves', async () => {
    const props = baseProps();
    render(<ScheduleGrid {...props} />);
    cell('dr Anna Nowak, 07:00, poza dyżurem').focus();

    await userEvent.keyboard('{ArrowDown}');
    expect(cell('dr Anna Nowak, 07:15, poza dyżurem')).toHaveFocus();
    await userEvent.keyboard('{ArrowRight}');
    expect(cell('dr Jan Kowalski, 07:15, poza dyżurem')).toHaveFocus();
    await userEvent.keyboard('{Home}');
    expect(cell('dr Anna Nowak, 07:15, poza dyżurem')).toHaveFocus();
    await userEvent.keyboard('{End}');
    expect(cell('dr Jan Kowalski, 07:15, poza dyżurem')).toHaveFocus();
    await userEvent.keyboard('{PageDown}'); // +1 godzina = 4 sloty
    expect(cell('dr Jan Kowalski, 08:15, wolny')).toHaveFocus();
    await userEvent.keyboard('{PageUp}');
    expect(cell('dr Jan Kowalski, 07:15, poza dyżurem')).toHaveFocus();

    // aktywacja wolnego slotu klawiszem
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{ArrowDown}{ArrowLeft}'); // 08:00 dr Anna
    expect(cell('dr Anna Nowak, 08:00, wolny')).toHaveFocus();
    await userEvent.keyboard('{Enter}');
    expect(props.onSlotSelect).toHaveBeenCalledWith({ columnId: 'a', start: at('08:00') });

    // aktywacja zajętej komórki otwiera wizytę
    await userEvent.keyboard('{ArrowDown}{ArrowDown}{ArrowDown}{ArrowDown}');
    expect(cell('dr Anna Nowak, 09:00, zajęty, Konsultacja')).toHaveFocus();
    await userEvent.keyboard('{Enter}');
    expect(props.onAppointmentSelect).toHaveBeenCalledWith('a1');

    await userEvent.keyboard('{Escape}');
    expect(document.body).toHaveFocus();
  });

  it('does not move focus beyond the grid edges', async () => {
    render(<ScheduleGrid {...baseProps()} />);
    cell('dr Anna Nowak, 07:00, poza dyżurem').focus();
    await userEvent.keyboard('{ArrowUp}{ArrowLeft}');
    expect(cell('dr Anna Nowak, 07:00, poza dyżurem')).toHaveFocus();
  });

  it('marks the selected appointment', () => {
    const { container } = render(<ScheduleGrid {...baseProps({ selectedAppointmentId: 'a1' })} />);
    expect(container.querySelector('[data-appt="a1"]')?.className).toContain('ring-2');
  });
});

describe('ScheduleGrid: dostępność', () => {
  it('has no axe violations (small grid; axe w jsdom jest wolne dla setek komórek)', async () => {
    const { container } = render(<ScheduleGrid {...baseProps({ endTime: '10:00' })} />);
    expect(within(container).getByRole('grid')).toBeInTheDocument();
    await expectNoA11yViolations(container);
  });
});

describe('generator danych próbnych', () => {
  it('produces ~250 non-overlapping appointments for 30 doctors', () => {
    const data = generateScheduleData({ day, doctors: 30 });
    expect(data.columns).toHaveLength(30);
    expect(data.bookedCount).toBeGreaterThan(180);
    expect(data.bookedCount).toBeLessThan(320);
    for (const list of Object.values(data.appointments)) {
      const active = list.filter((a) => a.status !== 'cancelled').sort((x, y) => x.start - y.start);
      active.forEach((a, i) => {
        const next = active[i + 1];
        if (next) expect(a.end).toBeLessThanOrEqual(next.start);
      });
    }
    expect(
      Object.values(data.appointments)
        .flat()
        .some((a) => a.status === 'cancelled'),
    ).toBe(true);
  });

  it('is deterministic for a seed', () => {
    expect(generateScheduleData({ day, seed: 7 })).toEqual(generateScheduleData({ day, seed: 7 }));
  });
});
