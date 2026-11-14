import {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  memo,
  type KeyboardEvent,
  type MouseEvent,
} from 'react';
import type { ScheduleAppointment, ScheduleGridProps } from './types';
import { combine, type Instant } from '@/shared/lib/datetime';
import { cn } from '@/shared/lib/cn';
import { Skeleton } from '@/shared/ui/display';

/** Powyżej tylu kolumn renderujemy tylko widoczne (wirtualizacja kolumn). */
export const VIRTUALIZE_ABOVE = 12;
const COLUMN_WIDTH_PX = 128;
const OVERSCAN = 2;
const FALLBACK_VIEWPORT_PX = 1200;
const MINUTE_MS = 60_000;

const toMinutes = (hhmm: string): number => {
  const [h, m] = hhmm.split(':').map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
};
const toLabel = (minutes: number): string =>
  `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;

const STATUS_CLASS: Record<ScheduleAppointment['status'], string> = {
  booked: 'bg-primary text-primary-foreground',
  completed: 'bg-success text-success-foreground',
  cancelled: 'hidden',
};

interface ColumnModel {
  base: Instant;
  occupant: (ScheduleAppointment | null)[];
  working: boolean[];
  blocks: { appt: ScheduleAppointment; row: number; span: number }[];
}

interface CellProps {
  ci: number;
  r: number;
  label: string;
  working: boolean;
  occupied: boolean;
  isActive: boolean;
}

/** Komórka siatki: memoizowana po prymitywach, więc przewijanie renderuje tylko nowe kolumny. */
const GridCell = memo(function GridCell({ ci, r, label, working, occupied, isActive }: CellProps) {
  return (
    <div
      role="gridcell"
      aria-colindex={ci + 2}
      aria-label={label}
      aria-disabled={!occupied && !working ? true : undefined}
      data-col={ci}
      data-row={r}
      tabIndex={isActive ? 0 : -1}
      className={cn(
        'border-r border-b border-border',
        working ? 'bg-background hover:bg-accent' : 'bg-muted',
      )}
      style={{ gridColumn: ci + 2, gridRow: r + 2 }}
    />
  );
});

/**
 * Siatka terminarza: CSS grid, bloki wizyt w komórkach siatki (grid-row), wzorzec ARIA `grid` z ruchomym
 * tabindex (jedna komórka w kolejności tabulacji), zdarzenia delegowane na kontenerze. Anulowane wizyty
 * nie zajmują slotu. Limit: godziny widoku nie powinny obejmować nocy zmiany czasu (slot = baza + n*krok).
 */
export function ScheduleGrid({
  columns,
  day,
  slotMinutes,
  startTime,
  endTime,
  workingIntervals,
  appointments,
  selectedAppointmentId,
  onSlotSelect,
  onAppointmentSelect,
  loading = false,
}: ScheduleGridProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const startMin = toMinutes(startTime);
  const rowCount = Math.max(0, Math.floor((toMinutes(endTime) - startMin) / slotMinutes));
  const virtualized = columns.length > VIRTUALIZE_ABOVE;

  const [active, setActive] = useState({ col: 0, row: 0 });
  const [range, setRange] = useState({ from: 0, to: Math.min(columns.length, VIRTUALIZE_ABOVE) });
  const focusRequested = useRef(false);

  const model = useMemo<ColumnModel[]>(
    () =>
      columns.map((column) => {
        const base = combine(column.day ?? day, startTime);
        const slotMs = slotMinutes * MINUTE_MS;
        const occupant: (ScheduleAppointment | null)[] = Array.from(
          { length: rowCount },
          () => null,
        );
        const blocks: ColumnModel['blocks'] = [];
        for (const appt of appointments[column.id] ?? []) {
          if (appt.status === 'cancelled') continue;
          const from = Math.max(0, Math.floor((appt.start - base) / slotMs));
          const to = Math.min(rowCount, Math.ceil((appt.end - base) / slotMs));
          if (to <= from) continue;
          blocks.push({ appt, row: from, span: to - from });
          for (let r = from; r < to; r++) occupant[r] = appt;
        }
        const intervals = workingIntervals[column.id] ?? [];
        const working = Array.from({ length: rowCount }, (_, r) => {
          const slotStart = base + r * slotMs;
          return intervals.some((i) => i.start <= slotStart && slotStart + slotMs <= i.end);
        });
        return { base, occupant, working, blocks };
      }),
    [columns, day, startTime, slotMinutes, rowCount, appointments, workingIntervals],
  );

  // Wirtualizacja kolumn: widoczny zakres z przewinięcia poziomego
  const updateRange = useCallback(() => {
    if (!virtualized) return;
    const el = containerRef.current;
    const width = el && el.clientWidth > 0 ? el.clientWidth : FALLBACK_VIEWPORT_PX;
    const left = el?.scrollLeft ?? 0;
    const from = Math.max(0, Math.floor(left / COLUMN_WIDTH_PX) - OVERSCAN);
    const to = Math.min(columns.length, Math.ceil((left + width) / COLUMN_WIDTH_PX) + OVERSCAN);
    setRange((prev) => (prev.from === from && prev.to === to ? prev : { from, to }));
  }, [virtualized, columns.length]);

  useLayoutEffect(() => {
    updateRange();
  }, [updateRange]);

  // Fokus po zmianie aktywnej komórki (po renderze, bo kolumna mogła dopiero wejść do zakresu)
  useEffect(() => {
    if (!focusRequested.current) return;
    const cell = containerRef.current?.querySelector<HTMLElement>(
      `[data-col="${String(active.col)}"][data-row="${String(active.row)}"]`,
    );
    if (cell) {
      cell.focus();
      focusRequested.current = false;
    }
  }, [active, range]);

  const moveTo = useCallback(
    (col: number, row: number) => {
      const c = Math.min(Math.max(col, 0), columns.length - 1);
      const r = Math.min(Math.max(row, 0), rowCount - 1);
      focusRequested.current = true;
      setActive({ col: c, row: r });
      const el = containerRef.current;
      if (virtualized && el) {
        const colLeft = c * COLUMN_WIDTH_PX;
        if (colLeft < el.scrollLeft) el.scrollLeft = colLeft;
        else if (colLeft + COLUMN_WIDTH_PX > el.scrollLeft + el.clientWidth) {
          el.scrollLeft = colLeft + COLUMN_WIDTH_PX - el.clientWidth;
        }
        updateRange();
      }
    },
    [columns.length, rowCount, virtualized, updateRange],
  );

  const activate = useCallback(
    (col: number, row: number) => {
      const m = model[col];
      const column = columns[col];
      if (!m || !column) return;
      const occupant = m.occupant[row];
      if (occupant) {
        onAppointmentSelect(occupant.id);
      } else if (m.working[row]) {
        onSlotSelect({
          columnId: column.id,
          start: (m.base + row * slotMinutes * MINUTE_MS) as Instant,
        });
      }
    },
    [model, columns, slotMinutes, onSlotSelect, onAppointmentSelect],
  );

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const cell = (event.target as HTMLElement).closest<HTMLElement>('[data-col]');
    if (!cell) return;
    const col = Number(cell.dataset.col);
    const row = Number(cell.dataset.row);
    const hour = Math.max(1, Math.round(60 / slotMinutes));
    const moves: Record<string, [number, number]> = {
      ArrowUp: [col, row - 1],
      ArrowDown: [col, row + 1],
      ArrowLeft: [col - 1, row],
      ArrowRight: [col + 1, row],
      Home: [0, row],
      End: [columns.length - 1, row],
      PageUp: [col, row - hour],
      PageDown: [col, row + hour],
    };
    const target = moves[event.key];
    if (target) {
      event.preventDefault();
      moveTo(target[0], target[1]);
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      activate(col, row);
    } else if (event.key === 'Escape') {
      cell.blur();
    }
  };

  const onClick = (event: MouseEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement;
    const block = target.closest<HTMLElement>('[data-appt]');
    if (block?.dataset.appt) {
      onAppointmentSelect(block.dataset.appt);
      return;
    }
    const cell = target.closest<HTMLElement>('[data-col]');
    if (!cell) return;
    const col = Number(cell.dataset.col);
    const row = Number(cell.dataset.row);
    setActive({ col, row });
    activate(col, row);
  };

  if (loading) {
    return (
      <div
        role="status"
        aria-busy="true"
        aria-label="Ładowanie terminarza"
        className="flex flex-col gap-2"
      >
        <Skeleton className="h-8 w-full" />
        <Skeleton className="h-64 w-full" />
      </div>
    );
  }

  const visible = virtualized ? range : { from: 0, to: columns.length };
  const shown = columns.slice(visible.from, visible.to);
  const rows = Array.from({ length: rowCount }, (_, r) => r);
  const template = `4rem repeat(${String(columns.length)}, ${String(COLUMN_WIDTH_PX)}px)`;

  return (
    <div
      ref={containerRef}
      role="grid"
      tabIndex={-1}
      aria-label="Terminarz"
      aria-rowcount={rowCount + 1}
      aria-colcount={columns.length + 1}
      className="relative max-h-[70vh] overflow-auto rounded-md border border-border text-xs"
      onScroll={updateRange}
      onKeyDown={onKeyDown}
      onClick={onClick}
    >
      {/* Wiersze ARIA jako display: contents, żeby komórki zostały w siatce CSS */}
      <div className="grid" style={{ gridTemplateColumns: template, width: 'max-content' }}>
        <div role="row" aria-rowindex={1} className="contents">
          <div
            role="columnheader"
            aria-colindex={1}
            className="sticky top-0 left-0 z-30 border-r border-b border-border bg-background"
            style={{ gridColumn: 1, gridRow: 1 }}
          >
            <span className="sr-only">Godzina</span>
          </div>
          {shown.map((column, i) => {
            const ci = visible.from + i;
            return (
              <div
                key={column.id}
                role="columnheader"
                aria-colindex={ci + 2}
                className="sticky top-0 z-20 border-r border-b border-border bg-background px-1 py-1 font-medium"
                style={{ gridColumn: ci + 2, gridRow: 1 }}
              >
                <div className="truncate">{column.label}</div>
                {column.subLabel ? (
                  <div className="truncate font-normal text-muted-foreground">
                    {column.subLabel}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
        {rows.map((r) => {
          const time = toLabel(startMin + r * slotMinutes);
          return (
            <div key={r} role="row" aria-rowindex={r + 2} className="contents">
              <div
                role="rowheader"
                aria-colindex={1}
                className="sticky left-0 z-10 border-r border-b border-border bg-background px-1 text-muted-foreground"
                style={{ gridColumn: 1, gridRow: r + 2 }}
              >
                {time}
              </div>
              {shown.map((column, i) => {
                const ci = visible.from + i;
                const m = model[ci];
                if (!m) return null;
                const occupant = m.occupant[r];
                const working = m.working[r] ?? false;
                const state = occupant
                  ? `zajęty, ${occupant.label}`
                  : working
                    ? 'wolny'
                    : 'poza dyżurem';
                const isActive = active.col === ci && active.row === r;
                return (
                  <GridCell
                    key={column.id}
                    ci={ci}
                    r={r}
                    label={`${column.label}, ${time}, ${state}`}
                    working={working}
                    occupied={Boolean(occupant)}
                    isActive={isActive}
                  />
                );
              })}
            </div>
          );
        })}
        {shown.flatMap((_column, i) => {
          const ci = visible.from + i;
          return (model[ci]?.blocks ?? []).map(({ appt, row, span }) => (
            <div
              key={appt.id}
              aria-hidden="true"
              data-appt={appt.id}
              className={cn(
                'z-[5] m-px cursor-pointer overflow-hidden rounded px-1',
                STATUS_CLASS[appt.status],
                selectedAppointmentId === appt.id && 'ring-2 ring-ring ring-offset-1',
              )}
              style={{ gridColumn: ci + 2, gridRow: `${String(row + 2)} / span ${String(span)}` }}
            >
              {appt.label}
            </div>
          ));
        })}
      </div>
    </div>
  );
}
