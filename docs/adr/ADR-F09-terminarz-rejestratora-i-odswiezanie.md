# ADR-F09: Terminarz rejestratora: siatka i odświeżanie danych

Status: przyjęte (etap 1a frontendu, 2026-11-14)

## Kontekst

Prototyp `ScheduleGrid` z etapu 0 (ADR-F04) przechodzi do wersji produkcyjnej. W terminarzu pracuje kilku rejestratorów naraz, więc widok szybko się dezaktualizuje.

## Decyzja

**Siatka dnia:** kolumny to aktywni lekarze z `GET /doctors` (rejestrator może ukrywać kolumny, domyślnie widoczni wszyscy), wiersze co `slotStepMinutes` z `GET /me` (domyślnie 15). Przedziały pracy każdego lekarza pochodzą z `GET /doctors/{id}/schedule` (jedno zapytanie na kolumnę), wizyty dnia z jednego `GET /reception/appointments` bez filtra lekarza, a granice widoku to pełne godziny obejmujące przedziały pracy (domyślnie 07:00-19:00). Wolny slot otwiera `BookForPatientDialog`, wizyta otwiera panel z akcjami "Przesuń" i "Odwołaj" (bez ograniczenia 24 h). Odwołane wizyty nie zajmują slotu w siatce; lista odwołanych tego dnia jest za przełącznikiem "Pokaż odwołane".

**Odświeżanie:** wizyty dnia co 60 s (`refetchInterval`), po powrocie do karty (`refetchOnWindowFocus` z domyślnej konfiguracji klienta zapytań) oraz po każdej akcji i każdym błędzie akcji (unieważnienie). Błąd zapytania pokazuje komunikat z ponowieniem i nie zmienia wybranego dnia, bo dzień jest stanem komponentu, a nie wynikiem zapytania. Nie używamy WebSocketów ani push: przy kilku rejestratorach odpytywanie co minutę wystarcza, a konflikty i tak rozstrzyga baza (ADR-F08).

**Grafik tygodniowy lekarza** (`MySchedulePage`, podgląd na liście lekarzy) to ta sama siatka w trybie siedmiu kolumn, po jednej na dzień (`column.day`).

## Konsekwencje

Dwóch rejestratorów może zobaczyć ten sam wolny slot do 60 s po zajęciu; skutkiem jest komunikat o kolizji, nie podwójna rezerwacja. Liczba zapytań rośnie liniowo z liczbą widocznych lekarzy (przy 30 lekarzach: 31 zapytań co zmianę dnia); w razie potrzeby backend dostanie zbiorczy endpoint grafików. Siatka nie obsługuje dni zmiany czasu w obrębie godzin widoku (ograniczenie z ADR-F04).

## Odrzucone opcje

- WebSocket lub SSE: dodatkowa infrastruktura bez zysku dla kilku użytkowników.
- Jedno zapytanie o grafiki wszystkich lekarzy: backend 1a go nie udostępnia.
