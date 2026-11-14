import { addDays, formatLocalDate, today, weekStart, type LocalDate } from '@/shared/lib/datetime';
import { Button } from '@/shared/ui/button';

interface WeekNavProps {
  /** Poniedziałek wyświetlanego tygodnia. */
  start: LocalDate;
  onChange: (start: LocalDate) => void;
}

/** Nawigacja po tygodniach: poprzedni, bieżący, następny, z czytelnym zakresem dat. */
export function WeekNav({ start, onChange }: WeekNavProps) {
  return (
    <nav aria-label="Wybór tygodnia" className="mb-3 flex flex-wrap items-center gap-2">
      <Button
        variant="outline"
        onClick={() => {
          onChange(addDays(start, -7));
        }}
      >
        Poprzedni tydzień
      </Button>
      <Button
        variant="outline"
        onClick={() => {
          onChange(weekStart(today()));
        }}
      >
        Ten tydzień
      </Button>
      <Button
        variant="outline"
        onClick={() => {
          onChange(addDays(start, 7));
        }}
      >
        Następny tydzień
      </Button>
      <span aria-live="polite" className="text-sm font-medium">
        {formatLocalDate(start)} – {formatLocalDate(addDays(start, 6))}
      </span>
    </nav>
  );
}
