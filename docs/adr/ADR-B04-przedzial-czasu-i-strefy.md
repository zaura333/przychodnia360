# ADR-B04: Przedział czasu i strefy

Status: przyjęte (etap 0 backendu, 2026-11-14)

## Kontekst

Wizyty nie mogą się na siebie nakładać u jednego wykonawcy, a grafiki pracy są definiowane lokalnie ("pon-pt 8:00-16:00"), więc zmiana czasu letniego i zimowego nie może przesuwać godzin przyjęć. Frontend ma analogiczną zasadę (ADR-F05).

## Decyzja

- Przedział wizyty to `tstzrange` w konwencji `[start, end)` (koniec wyłączny), z `CHECK (NOT isempty(time_range))`. Wizyty kolejne bez przerwy (`end` jednej = `start` drugiej) nie kolidują.
- Kolizje pilnuje baza: ograniczenie wykluczające `EXCLUDE USING gist (employee_id WITH =, time_range WITH &&) WHERE (status = 'SCHEDULED')` (rozszerzenie `btree_gist`). Aplikacja tłumaczy naruszenie na `409`; test współbieżności dowodzi, że jedna z dwóch równoległych rezerwacji przegrywa.
- Grafiki pracy są zapisane **lokalnie** (dzień tygodnia, godzina lokalna) i przeliczane na `timestamptz` w strefie `Europe/Warsaw` przy generowaniu slotów. Dzięki temu "8:00" zostaje "8:00" po zmianie czasu.
- W Javie czas bezwzględny to `Instant`, czas lokalny to `LocalDate` i `LocalTime` ze `ZoneId` placówki; `Clock` jest wstrzykiwany. API wymienia znaczniki ISO-8601 z offsetem lub `Z`.
- Dni zmiany czasu (marzec, październik) mają testy: brak slotów w nieistniejącej godzinie, powtórzona godzina generowana raz.

## Konsekwencje

- Zapytania o wolne terminy korzystają z operatorów zakresów i indeksu GiST.
- Strefa jest na razie stała (`Europe/Warsaw`); wielostrefowość wymagałaby kolumny strefy w placówce.

## Odrzucone opcje

- Dwie kolumny `start_at` i `end_at`: kolizje wymagają kodu aplikacji lub triggera, a wyścig zostaje.
- Grafik zapisany w UTC: godziny przyjęć przesuwają się o godzinę dwa razy w roku.
- Kolizje sprawdzane tylko w aplikacji: wyścig między transakcjami.
