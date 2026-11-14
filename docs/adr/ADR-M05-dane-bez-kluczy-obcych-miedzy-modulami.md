# ADR-M05: Dane: brak kluczy obcych między modułami

Status: przyjęte (fala 0R, 2026-11-14; zlecenie właściciela „zrób 0R"). Część dotycząca `subject_type/subject_id` odroczona do wydzielenia `iam-service` (fala 5.1).

## Kontekst

Mapa szwów (fala 0, B3) pokazała, że kod modułów jest już czysty (importy tylko przez pakiety `api`, SQL tylko do własnych tabel), a naruszenia granic siedziały w schemacie: cykl `users` ↔ `patients/employees`, klucze `appointments → patients/services/users` oraz `tenant_id → tenants` we wszystkich tabelach. Przy podziale baz per usługa takie klucze nie mogą istnieć.

## Decyzja

1. **Żaden klucz obcy nie przekracza granicy modułu** (przyszłej usługi). Moduły: `iam` (tenants, users, refresh_tokens, password_reset_tokens), `catalog` (service_categories, services), `patients`, `scheduling` (employees, work_schedules, schedule_exceptions, appointments).
2. Migracja `V9__drop_cross_module_foreign_keys.sql` usuwa takie klucze (zgodnie wstecz, tylko ograniczenia). Zostają klucze wewnątrz modułu (np. `appointments → employees`, `services → service_categories`, tokeny → `users`) i ograniczenia wykluczające po pracowniku i pacjencie (działają na identyfikatorach).
3. Powiązania zostają jako identyfikatory (`user_id`, `patient_id`, `employee_id`, `service_id`, `tenant_id`). Spójność zapewniają fasady modułów (sprawdzenie istnienia przed zapisem), RLS (`tenant_id`) i, po wydzieleniu usług, zdarzenia oraz zadanie uzgadniające.
4. **Test `ModuleBoundariesFkTest`** (blokada w CI): każda tabela ma przypisany moduł, a żaden klucz obcy nie łączy różnych modułów. Nowa tabela wymaga wpisu w mapie modułów.
5. **Retencja:** każda tabela ma klasę retencji (RC-n) albo status słownika `DICT` w `retention-classes.csv`; test `RetentionClassCoverageTest` blokuje tabelę bez klasy (09).
6. **Odroczone:** zastąpienie `users.patient_id/employee_id` parą `subject_type/subject_id` (propozycja D-041) oraz zadanie uzgadniające konto ↔ podmiot. Powód: zmiana kodu i migracji danych bez korzyści przed wydzieleniem IAM; po usunięciu kluczy obcych obecne kolumny pełnią tę samą rolę identyfikatorów.

## Konsekwencje

- Utrata gwarancji bazy (usunięcie podmiotu nie jest blokowane kluczem): kasowanie robią moduły i orkiestracja retencji, nie kaskady SQL. W monolicie spójność pilnują fasady i testy, po podziale zdarzenia i uzgadnianie.
- Migracja jest nieodwracalna w tym sensie, że ewentualne przywrócenie kluczy wymaga nowej migracji; dane nie są modyfikowane.
- Wydzielenie usługi to przesunięcie tabel do nowej bazy bez zmiany schematu.

## Odrzucone opcje

- **Zostawić klucze do chwili podziału:** podział baz wymagałby wtedy migracji schematu i kodu jednocześnie.
- **Klucze „miękkie" (deferred, NOT VALID):** nadal wiążą bazy; nie rozwiązują cyklu.
