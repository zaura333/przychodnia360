import { ApiError } from '@/shared/api/apiError';

export type BookingFailure =
  | { kind: 'slot-taken'; message: string }
  | { kind: 'patient-overlap'; message: string }
  | { kind: 'unprocessable'; message: string }
  | { kind: 'gone'; message: string }
  | { kind: 'other' };

/**
 * Klasyfikacja błędu rezerwacji lub przesunięcia po polu `type` Problem Details (nie po treści `detail`).
 * Wynik steruje kreatorem: gdzie wrócić i czy odświeżyć terminy.
 */
export function classifyBookingError(error: unknown): BookingFailure {
  if (!(error instanceof ApiError)) return { kind: 'other' };
  if (error.status === 409 && error.type === 'slot-taken') {
    return {
      kind: 'slot-taken',
      message: 'Ten termin został właśnie zajęty. Wybierz inną godzinę.',
    };
  }
  if (error.status === 409 && error.type === 'patient-overlap') {
    return {
      kind: 'patient-overlap',
      message: 'Masz już wizytę w tym czasie. Wybierz inną godzinę.',
    };
  }
  if (error.status === 422) {
    return {
      kind: 'unprocessable',
      message: 'Wybrany termin nie jest już dostępny. Wybierz inny z odświeżonej listy.',
    };
  }
  if (error.status === 403 || error.status === 404) {
    return {
      kind: 'gone',
      message: 'Nie można zarezerwować tej wizyty. Zacznij od początku.',
    };
  }
  return { kind: 'other' };
}
