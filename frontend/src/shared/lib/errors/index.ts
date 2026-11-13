import { ApiError } from '@/shared/api/apiError';

export interface DescribedError {
  title: string;
  message: string;
  requestId?: string | undefined;
}

interface Text {
  title: string;
  message: string;
}

/**
 * Słownik typów Problem Details (ostatni segment `type`) na komunikaty po polsku. Treść z serwera
 * (`detail`) nigdy nie trafia na ekran niefiltrowana; lista rozszerzana w 1a/1b razem z backendem.
 */
const BY_TYPE: Record<string, Text> = {
  'slot-taken': {
    title: 'Termin jest już zajęty',
    message: 'Ktoś zdążył zarezerwować ten termin. Wybierz inną godzinę.',
  },
  'patient-overlap': {
    title: 'Masz już wizytę w tym czasie',
    message: 'W wybranym czasie masz zaplanowaną inną wizytę.',
  },
  'account-locked': {
    title: 'Konto jest czasowo zablokowane',
    message: 'Zbyt wiele nieudanych prób logowania. Spróbuj ponownie później.',
  },
  'account-blocked': {
    title: 'Konto jest zablokowane',
    message: 'Skontaktuj się z administratorem placówki.',
  },
  'duplicate-pesel': {
    title: 'Pacjent o tym numerze PESEL już istnieje',
    message: 'Wyszukaj istniejącą kartę pacjenta zamiast tworzyć nową.',
  },
  validation: {
    title: 'Niepoprawne dane',
    message: 'Sprawdź zaznaczone pola i spróbuj ponownie.',
  },
};

const BY_STATUS: Record<number, Text> = {
  401: { title: 'Wymagane logowanie', message: 'Zaloguj się, aby kontynuować.' },
  403: { title: 'Brak uprawnień', message: 'Nie masz uprawnień do tej operacji.' },
  404: { title: 'Nie znaleziono', message: 'Szukany zasób nie istnieje lub został usunięty.' },
  409: { title: 'Konflikt', message: 'Dane zostały zmienione. Odśwież widok i spróbuj ponownie.' },
  422: { title: 'Niepoprawne dane', message: 'Sprawdź wprowadzone dane i spróbuj ponownie.' },
  429: { title: 'Zbyt wiele żądań', message: 'Odczekaj chwilę i spróbuj ponownie.' },
};

const SERVER: Text = {
  title: 'Błąd serwera',
  message: 'Po stronie serwera wystąpił problem. Spróbuj ponownie za chwilę.',
};
const NETWORK: Text = {
  title: 'Brak połączenia',
  message: 'Nie można połączyć z serwerem. Sprawdź połączenie z internetem.',
};
const TIMEOUT: Text = {
  title: 'Serwer nie odpowiada',
  message: 'Odpowiedź trwa zbyt długo. Spróbuj ponownie za chwilę.',
};
const GENERIC: Text = {
  title: 'Coś poszło nie tak',
  message: 'Wystąpił nieoczekiwany błąd. Podaj identyfikator, jeśli zgłaszasz problem.',
};

function pick(error: ApiError): Text {
  if (error.isNetwork) return NETWORK;
  if (error.isTimeout) return TIMEOUT;
  if (error.type && error.type in BY_TYPE) return BY_TYPE[error.type] ?? GENERIC;
  if (error.status >= 500) return SERVER;
  return BY_STATUS[error.status] ?? GENERIC;
}

/** Jedyna droga od błędu do tekstu na ekranie. Nieznany typ kończy się komunikatem ogólnym. */
export function describeError(error: unknown): DescribedError {
  if (error instanceof ApiError) {
    return { ...pick(error), requestId: error.requestId };
  }
  return { ...GENERIC };
}
