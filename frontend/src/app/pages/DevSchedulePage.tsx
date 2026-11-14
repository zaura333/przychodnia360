import { useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'react-router';
import type { LocalDate } from '@/shared/lib/datetime';
import { generateScheduleData } from '@/shared/test/scheduleData';
import { Button } from '@/shared/ui/button';
import { ScheduleGrid } from '@/shared/ui/schedule-grid/schedule-grid';

const DAY = '2026-06-10' as LocalDate;

/**
 * Strona pomiarowa prototypu terminarza (tylko dev oraz build z VITE_BENCH=true, nie trafia do produkcji).
 * Mierzy czas od kliknięcia do pierwszego namalowania siatki. Używana przez `npm run bench:schedule`.
 */
export function Component() {
  const [params] = useSearchParams();
  const columnCount = Number(params.get('columns') ?? 12);
  const data = useMemo(
    () => generateScheduleData({ day: DAY, doctors: columnCount }),
    [columnCount],
  );
  const [shown, setShown] = useState(false);
  const [renderMs, setRenderMs] = useState<string | null>(null);
  const [selected, setSelected] = useState<string>('-');
  const startedAt = useRef(0);

  useEffect(() => {
    if (!shown) return;
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        setRenderMs((performance.now() - startedAt.current).toFixed(1));
      });
    });
  }, [shown]);

  return (
    <main className="p-4">
      <h1 className="mb-2 text-xl font-semibold">Prototyp terminarza ({columnCount} kolumn)</h1>
      <Button
        onClick={() => {
          startedAt.current = performance.now();
          setShown(true);
        }}
      >
        Renderuj siatkę
      </Button>
      <output data-testid="render-ms" className="ml-4">
        {renderMs ?? ''}
      </output>
      <output data-testid="selected" className="ml-4">
        {selected}
      </output>
      <div className="mt-4">
        {shown ? (
          <ScheduleGrid
            columns={data.columns}
            day={DAY}
            slotMinutes={15}
            startTime="07:00"
            endTime="19:00"
            workingIntervals={data.workingIntervals}
            appointments={data.appointments}
            onSlotSelect={(s) => {
              setSelected(`slot ${s.columnId} ${String(s.start)}`);
            }}
            onAppointmentSelect={(id) => {
              setSelected(`appt ${id}`);
            }}
          />
        ) : null}
      </div>
    </main>
  );
}
