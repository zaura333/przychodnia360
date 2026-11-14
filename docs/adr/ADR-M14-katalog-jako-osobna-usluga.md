# ADR-M14: Katalog usług jako osobna usługa (`catalog-service`)

Status: przyjęte (2026-11-14; zlecenie właściciela „zacznij S1 split"). Odwraca odroczenie z `04-roadmap.md` (decyzja w fali 5): katalog jest pilotem podziału `clinic-core`.

## Kontekst

Plan podziału (`implementation-plan-split-clinic-core.md`) wybiera `catalog` na pilota (S1): moduł nie ma zależności wychodzących, jedynym konsumentem jest `appointments`, a powierzchnia jest mała (dwie tabele, dwa kontrolery). Pilot sprawdza cały wzorzec (JWKS, krawędź, zdalna fasada, CI, compose, baza per usługa) na najtańszym przypadku. Alternatywa z `06`: `catalog` zostaje częścią `scheduling` do czasu cenników płatników.

## Decyzja

1. `catalog` jest osobną usługą `catalog-service` z własną bazą `p360_catalog`, tabelami `service_categories` i `services` oraz łańcuchem Flyway od `V1`.
2. Kontrakt REST dla frontendu bez zmian (`/services/**`, `/service-categories`, `/admin/services/**`, `/admin/service-categories/**`); zmienia się tylko cel w tabeli routingu.
3. **Typ wykonawcy to wartość kontraktu** (`DOCTOR`, `MEDICAL_STAFF` jako łańcuch), nie klasa współdzielona między modułami (zrobione: `ServiceInfo.performerType` jest `String`, `EmployeeType.canPerform(String)`).
4. `scheduling` czyta usługi przez `CatalogFacade` z dwiema implementacjami (lokalna, zdalna REST z limitem czasu i circuit breakerem), przełączanymi właściwością; kopia `service_ref` zasilana zdarzeniami `ServiceCreated`/`ServiceUpdated`/`ServiceDeactivated` (tylko id i dane techniczne).
5. Cenniki płatników (fala 5) wejdą do `catalog-service`; jeśli okażą się mocno sprzężone z `scheduling`, usługi można scalić (cofnięcie kosztuje przeniesienie dwóch tabel).

## Konsekwencje

- Rezerwacja zależy od dostępności `catalog` (walidacja synchroniczna), dlatego wymaga limitu czasu, circuit breakera i czytelnego błędu; przeglądanie wizyt czyta kopię i działa przy awarii `catalog`.
- Dochodzi druga usługa do CI, compose i krawędzi (pierwsze sprawdzenie szablonu usługi, D-054).
- Wyjątek od „kontrakt API bez zmian": `GET /services?ids=` (batch) dla klienta zdalnego, o ile nie istnieje.

## Odrzucone opcje

- **Katalog w `scheduling` do fali 5:** pilot byłby droższy (`patient` ma PII i sagę), a wzorzec bez najtańszego przypadku.
- **Wspólna biblioteka z typem `PerformerType`:** wiąże usługi wersją kodu i łamie niezależne wdrażanie.
