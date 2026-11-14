import { useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useSlots } from '../hooks';
import type { SlotResponse } from '@/shared/api/generated/model';
import {
  formatDayLong,
  formatTime,
  parseInstant,
  toLocalDate,
  today,
  weekStart,
  type LocalDate,
} from '@/shared/lib/datetime';
import { cn } from '@/shared/lib/cn';
import { QueryState } from '@/shared/ui/query-state';
import { WeekNav } from '@/shared/ui/week-nav';

interface SlotPickerProps {
  employeeId: string;
  serviceId: string;
  /** Początek wybranego terminu (ISO) albo null. */
  value: string | null;
  onChange: (slot: SlotResponse) => void;
}

function groupByDay(slots: readonly SlotResponse[]): [LocalDate, SlotResponse[]][] {
  const groups = new Map<LocalDate, SlotResponse[]>();
  for (const slot of slots) {
    const day = toLocalDate(parseInstant(slot.start));
    groups.set(day, [...(groups.get(day) ?? []), slot]);
  }
  return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
}

/**
 * Wybór terminu: tydzień z nawigacją, wolne godziny pogrupowane po dniach. Każdy termin to przycisk
 * z pełną etykietą dla czytników ekranu; strzałki przenoszą fokus między terminami.
 */
export function SlotPicker({ employeeId, serviceId, value, onChange }: SlotPickerProps) {
  const [start, setStart] = useState(() => weekStart(today()));
  const slots = useSlots(employeeId, serviceId, start);
  const container = useRef<HTMLDivElement>(null);
  const groups = useMemo(() => groupByDay(slots.data ?? []), [slots.data]);

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const keys = ['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp'];
    if (!keys.includes(event.key)) return;
    const buttons = [
      ...(container.current?.querySelectorAll<HTMLButtonElement>('[data-slot]') ?? []),
    ];
    const index = buttons.indexOf(event.target as HTMLButtonElement);
    if (index < 0) return;
    event.preventDefault();
    const next = event.key === 'ArrowRight' || event.key === 'ArrowDown' ? index + 1 : index - 1;
    buttons[Math.min(Math.max(next, 0), buttons.length - 1)]?.focus();
  };

  return (
    <section aria-label="Wybór terminu">
      <WeekNav start={start} onChange={setStart} />
      <QueryState
        query={slots}
        isEmpty={(d) => d.length === 0}
        emptyTitle="Brak wolnych terminów w tym tygodniu, sprawdź kolejny"
      >
        {() => (
          // eslint-disable-next-line jsx-a11y/no-static-element-interactions -- delegowana nawigacja strzałkami po przyciskach
          <div ref={container} onKeyDown={onKeyDown} className="flex flex-col gap-3">
            {groups.map(([day, daySlots]) => (
              <div key={day}>
                <h3 className="mb-1 text-sm font-medium capitalize">{formatDayLong(day)}</h3>
                <ul className="flex flex-wrap gap-2">
                  {daySlots.map((slot) => {
                    const selected = value === slot.start;
                    const from = formatTime(parseInstant(slot.start));
                    const to = formatTime(parseInstant(slot.end));
                    return (
                      <li key={slot.start}>
                        <button
                          type="button"
                          data-slot
                          aria-pressed={selected}
                          aria-label={`${formatDayLong(day)}, ${from}–${to}`}
                          className={cn(
                            'touch-target rounded-md border px-3 text-sm',
                            selected
                              ? 'border-primary bg-primary text-primary-foreground'
                              : 'border-input bg-background hover:bg-muted',
                          )}
                          onClick={() => {
                            onChange(slot);
                          }}
                        >
                          {from}
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))}
          </div>
        )}
      </QueryState>
    </section>
  );
}
