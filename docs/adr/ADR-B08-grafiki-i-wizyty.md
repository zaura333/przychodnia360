# ADR-B08: Grafiki pracy i ograniczenia wizyt w bazie

Status: przyjęte (etap 1a, 2026-11-14)

## Kontekst

Rezerwacje muszą być poprawne przy równoległych żądaniach, a grafiki lekarzy przeżywać zmianę czasu (ADR-B04). Aplikacja ma tylko jedno zadanie, ale gwarancji nie można opierać na sprawdzeniu w kodzie przed zapisem (wyścig między transakcjami).

## Decyzja

**Grafik** to wpisy tygodniowe (`work_schedules`: dzień tygodnia, godziny lokalne, okres obowiązywania od-do włącznie) oraz wyjątki (`schedule_exceptions`: urlop na cały dzień albo dodatkowa zmiana w godzinach). Nakładające się dyżury tego samego pracownika odrzuca ograniczenie wykluczające z kolumnami generowanymi (`validity` jako `daterange`, `span` jako `tsrange` z godzin dnia), a dyżury stykające się są dozwolone. Przedziały pracy w oknie liczy `WorkingTimeCalculator` (grafik minus urlopy plus dodatkowe zmiany, scalone), a przeliczenia na czas bezwzględny wykonuje wyłącznie `ClinicTime`. Okno zapytań o grafik i sloty to najwyżej 32 dni (od dnia do dnia włącznie).

**Wizyty** mają `time_range tstzrange [start, end)`. Dwa ograniczenia wykluczające z warunkiem `status IN ('SCHEDULED', 'COMPLETED')`: jeden wykonawca nie ma dwóch nakładających się wizyt (`ex_appointments_employee`, błąd `slot-taken`), a jeden pacjent też nie (`ex_appointments_patient`, błąd `patient-overlap`). Odwołanie zwalnia termin, bo wizyta `CANCELLED` wypada z warunku. Naruszenie (SQLSTATE 23P01) tłumaczy jedno miejsce w repozytorium na `409`, rodzaj po nazwie ograniczenia.

**Reguły przed zapisem** (`BookingPolicy`, `422`): termin w przyszłości, w całości w jednym dyżurze, początek na siatce kroku (domyślnie 15 minut, minuty wielokrotne kroku), wykonawca aktywny i zgodny z typem usługi, usługa aktywna. Wolne terminy to przedziały pracy minus zajęte, pocięte na sloty o długości usługi; odczyt jest optymistyczny, a o rezerwacji decyduje baza.

**Usuwanie** usług i pracowników jest logiczne (flaga `active`); dezaktywacja pracownika z przyszłymi wizytami to `409`, a sprawdzenie realizuje punkt rozszerzenia `EmployeeDeactivationGuard` z modułu `appointments` (staff nie zna wizyt, brak cyklu modułów).

## Konsekwencje

- Test współbieżności (20 równoległych rezerwacji tego samego terminu: dokładnie jedna `201`, reszta `409`) jest bramką jakości i działa w każdym `verify`.
- Siatka slotów jest liczona względem pełnej godziny UTC. Kroki dzielące godzinę (5, 10, 15, 20, 30, 60 minut) działają poprawnie, bo strefa Europe/Warsaw ma pełne offsety godzinowe.
- Rejestrator tworzy rekord pacjenta bez konta (`POST /reception/patients`); założenie konta pacjenta przez rejestratora (link ustawienia hasła) jest do zrobienia osobno, bo wymaga zależności patients → identity, która tworzyłaby cykl modułów (decyzja D-032 w dzienniku decyzji).

## Odrzucone opcje

- Dwie kolumny `start_at` i `end_at` z kontrolą w aplikacji: wyścig między transakcjami.
- Blokady aplikacyjne (`SELECT ... FOR UPDATE` na wykonawcy): serializują wszystkie rezerwacje danego wykonawcy, a ograniczenie w bazie daje to samo bez blokady.
- Slotowanie z zapisem slotów w tabeli: duplikuje grafik i wymaga synchronizacji przy każdej zmianie.
