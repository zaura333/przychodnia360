# ADR-F05: Czas i strefy

Status: przyjęte (etap 0 frontendu, 2026-11-13)

## Kontekst

Błędy stref czasowych dwa razy w roku zmieniają godziny wizyt. Użytkownicy i urządzenia mogą być w dowolnej strefie.

## Decyzja

Jedyny moduł czasu: `shared/lib/datetime` (date-fns 4 i `@date-fns/tz`, strefa `Europe/Warsaw`). Chwila to `Instant` (branded number), data na ścianie to `LocalDate` (branded string). `addDays` liczy dni kalendarzowe, `dayRange` zwraca zakres półotwarty. Lint blokuje `new Date`, `Date.now`, `Intl.DateTimeFormat` i `toLocale*String` poza modułem. Testy uruchamiane w `TZ=America/Los_Angeles`, w tym dni zmiany czasu 2026-03-29 (23 h) i 2026-10-25 (25 h), a Playwright z `timezoneId: America/New_York`.

## Konsekwencje

Czas w teście jest niezależny od strefy urządzenia. Godzina nieistniejąca (luka wiosenna) przesuwa się do przodu, a godzina podwójna (jesień) wybiera pierwsze wystąpienie.

## Odrzucone opcje

Użycie `Date` w komponentach; biblioteki ze strefą urządzenia.
