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
  'invalid-credentials': {
    title: 'Nieprawidłowe dane logowania',
    message:
      'Sprawdź kod placówki, adres e-mail i hasło. Po kilku nieudanych próbach konto jest czasowo blokowane.',
  },
  'invalid-or-expired-token': {
    title: 'Link jest nieprawidłowy lub wygasł',
    message: 'Poproś o nowy link do ustawienia hasła.',
  },
  'email-taken': {
    title: 'Adres e-mail jest zajęty',
    message: 'Konto z tym adresem już istnieje.',
  },
  'slug-taken': {
    title: 'Kod placówki jest zajęty',
    message: 'Wybierz inny kod placówki.',
  },
  'slot-in-past': {
    title: 'Termin już minął',
    message: 'Wybierz termin w przyszłości.',
  },
  'outside-working-hours': {
    title: 'Termin poza godzinami pracy',
    message: 'Lekarz nie pracuje w wybranym czasie. Wybierz inny termin.',
  },
  'performer-mismatch': {
    title: 'Wykonawca nie wykonuje tej usługi',
    message: 'Wybierz innego wykonawcę lub inną usługę.',
  },
  'performer-inactive': {
    title: 'Wykonawca jest niedostępny',
    message: 'Wybierz innego wykonawcę.',
  },
  'service-inactive': {
    title: 'Usługa jest niedostępna',
    message: 'Ta usługa została wycofana z oferty.',
  },
  'slot-not-on-grid': {
    title: 'Niepoprawna godzina',
    message: 'Wybierz godzinę z listy wolnych terminów.',
  },
  'appointment-started': {
    title: 'Wizyta już się rozpoczęła',
    message: 'Wizyty, która się rozpoczęła lub minęła, nie można zmienić.',
  },
  'already-cancelled': {
    title: 'Wizyta jest już odwołana',
    message: 'Odśwież widok, aby zobaczyć aktualny stan.',
  },
  'appointment-not-scheduled': {
    title: 'Wizyta nie jest zaplanowana',
    message: 'Tej wizyty nie można zmienić.',
  },
  'concurrent-update': {
    title: 'Ktoś zmienił te dane',
    message: 'Odśwież widok i spróbuj ponownie.',
  },
  'schedule-overlap': {
    title: 'Dyżury nakładają się',
    message: 'Popraw godziny: dyżury w tym samym dniu nie mogą się nakładać.',
  },
  'category-exists': {
    title: 'Kategoria już istnieje',
    message: 'Wybierz inną nazwę kategorii.',
  },
  'category-in-use': {
    title: 'Kategoria jest używana',
    message: 'Nie można usunąć kategorii powiązanej z usługami.',
  },
  'employee-has-future-appointments': {
    title: 'Pracownik ma przyszłe wizyty',
    message: 'Przesuń lub odwołaj wizyty pracownika, a potem go dezaktywuj.',
  },
  'no-patient-record': {
    title: 'Brak danych pacjenta',
    message: 'To konto nie ma powiązanej karty pacjenta.',
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

/** Mapuje błędy pól z Problem Details (`errors[]`) na komunikaty przy polach formularza. */
export function fieldErrorsOf(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {};
  return Object.fromEntries(error.errors.map((e) => [e.field, e.message]));
}

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
