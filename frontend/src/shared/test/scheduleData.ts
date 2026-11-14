import { combine, type Instant, type LocalDate } from '@/shared/lib/datetime';
import type {
  ScheduleAppointment,
  ScheduleColumn,
  WorkingInterval,
} from '@/shared/ui/schedule-grid/types';

/** Deterministyczny generator liczb pseudolosowych (powtarzalne pomiary i testy). */
function mulberry32(seed: number): () => number {
  let a = seed;
  return () => {
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const MINUTE_MS = 60_000;
const DURATIONS = [15, 30, 30, 45, 60] as const;
const LABELS = ['Konsultacja', 'Kontrola', 'Badanie', 'Szczepienie', 'Zabieg'];

export interface ScheduleFixture {
  columns: ScheduleColumn[];
  workingIntervals: Record<string, WorkingInterval[]>;
  appointments: Record<string, ScheduleAppointment[]>;
  bookedCount: number;
}

/**
 * Dane próbne: dyżury z przerwą, wizyty 15-60 min bez nakładania w kolumnie (jak ograniczenie w bazie),
 * kilka odwołanych (mogą nakładać się na aktywne). Doba 07:00-19:00, krok 15 min = 48 wierszy.
 */
export function generateScheduleData(options: {
  day: LocalDate;
  doctors?: number;
  targetAppointments?: number;
  seed?: number;
}): ScheduleFixture {
  const { day, doctors = 30, targetAppointments = 250, seed = 1 } = options;
  const rnd = mulberry32(seed);
  const at = (hhmm: string): Instant => combine(day, hhmm);
  const pick = <T>(items: readonly T[]): T => items[Math.floor(rnd() * items.length)] as T;

  const columns: ScheduleColumn[] = [];
  const workingIntervals: Record<string, WorkingInterval[]> = {};
  const appointments: Record<string, ScheduleAppointment[]> = {};
  let booked = 0;
  const perDoctor = Math.ceil(targetAppointments / doctors);

  for (let d = 0; d < doctors; d++) {
    const id = `doc-${String(d + 1)}`;
    columns.push({
      id,
      label: `dr Lekarz ${String(d + 1)}`,
      subLabel: pick(['Interna', 'Pediatria', 'Kardiologia']),
    });
    const shiftStart = 7 * 60 + Math.floor(rnd() * 9) * 15; // 07:00-09:00
    const shiftEnd = 15 * 60 + Math.floor(rnd() * 17) * 15; // 15:00-19:00
    const breakStart = 11 * 60 + 30 + Math.floor(rnd() * 3) * 15;
    const toHhmm = (m: number) =>
      `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
    const intervals: WorkingInterval[] = [
      { start: at(toHhmm(shiftStart)), end: at(toHhmm(breakStart)) },
      { start: at(toHhmm(breakStart + 30)), end: at(toHhmm(shiftEnd)) },
    ];
    workingIntervals[id] = intervals;

    const list: ScheduleAppointment[] = [];
    const taken: [number, number][] = [];
    for (let attempt = 0; attempt < perDoctor * 6 && list.length < perDoctor; attempt++) {
      const interval = pick(intervals);
      const duration = pick(DURATIONS);
      const slots = Math.floor((interval.end - interval.start) / (15 * MINUTE_MS));
      const startSlot = Math.floor(rnd() * Math.max(1, slots - duration / 15 + 1));
      const start = interval.start + startSlot * 15 * MINUTE_MS;
      const end = start + duration * MINUTE_MS;
      if (end > interval.end) continue;
      if (taken.some(([s, e]) => start < e && end > s)) continue;
      taken.push([start, end]);
      list.push({
        id: `${id}-a${String(list.length + 1)}`,
        start: start as Instant,
        end: end as Instant,
        label: pick(LABELS),
        status: rnd() < 0.2 ? 'completed' : 'booked',
      });
    }
    booked += list.length;
    if (d % 4 === 0 && list[0]) {
      // odwołana wizyta w terminie już zajętym (nie blokuje slotu)
      list.push({ ...list[0], id: `${id}-cancelled`, status: 'cancelled', label: 'Odwołana' });
    }
    appointments[id] = list;
  }
  return { columns, workingIntervals, appointments, bookedCount: booked };
}
