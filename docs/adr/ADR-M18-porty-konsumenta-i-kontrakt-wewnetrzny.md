# ADR-M18: porty konsumenta, kontrakt wewnętrzny usług i usunięcie `linkAccount`

Status: przyjęte (2026-11-22; uzupełnia ADR-M16 i ADR-M17).

## Kontekst

Po wydzieleniu `catalog`, `patient`, `iam` i `scheduling` wywołania między usługami szły przez interfejsy Javy skopiowane z
usługi docelowej (np. `PatientFacade` w `iam` i `scheduling`) i wewnętrzne API `/api/v1/internal/**`, które nie trafia do
eksportów OpenAPI (ADR-M16, ADR-M17 pkt 5). Skutki:

- kopie interfejsów miały operacje, których konsument nie używa, i rozjeżdżały się z faktycznym użyciem;
- wywołania między usługami nie miały kontraktu sprawdzanego maszynowo: konsument testował adapter na atrapie,
  dostawca swoje endpointy, a nic nie łączyło obu stron;
- `PUT /api/v1/internal/patients/{id}/account` (`linkAccount`) nie ma żadnego klienta w usługach. Wołała go rejestracja
  pacjenta do zmiany `e9d6214` (15.11.2026), która przeszła na idempotentne `ensure(patientId, ..., userId)`: jedno
  wywołanie zakłada pacjenta i ustawia powiązanie z kontem, a powtórzenie trafia w ten sam rekord. Od tego czasu
  endpoint, metoda w `PatientFacade` i `PatientService` oraz `LinkAccountRequest` zostały wyłącznie dla testów.

## Decyzja

1. **Port należy do konsumenta i jest minimalny.** `iam` ma `PatientRegistry` (`ensure`, `find`), `scheduling` ma
   `PatientDirectory` (`ensure`, `find`, `findAll`), `CatalogFacade` i `IdentityFacade` mają tylko operacje używane w
   `scheduling`. Nazwa opisuje rolę, a nie cudzy serwis. Adaptery HTTP to `Remote*`. Pełny interfejs dostawcy
   (`PatientFacade` w `patient`) zostaje w usłudze, która go implementuje.
2. **Kontrakt wewnętrzny jest osobnym eksportem** `api/internal/<usługa>.json` (tylko ścieżki `/api/v1/internal/**`). Nie
   wchodzi do agregatu `api/openapi.json`, klienta frontendu ani routingu krawędzi. Adapter każdego konsumenta ma test
   zgodności z kontraktem dostawcy (`Remote*ContractTest`, `InternalApiContract`), a CI uruchamia `oasdiff breaking`
   dla każdego `api/internal/*.json` względem bazy PR.
3. **`linkAccount` jest usunięty:** endpoint `PUT /api/v1/internal/patients/{id}/account`, metoda `linkAccount` w
   `PatientFacade` i `PatientService`, `LinkAccountRequest` i test, który go wołał. Powiązanie pacjenta z kontem
   ustawia `ensure` z `userId` (nadpisuje poprzednie, np. z wycofanej próby rejestracji). Wewnętrzne API `patient`
   ma trzy operacje: `GET /patients`, `GET /patients/{id}`, `PUT /patients/{id}`.

## Konsekwencje

- Każda operacja wewnętrznego API ma co najmniej jednego konsumenta, a jej użycie widać w `Remote*` i w teście
  kontraktowym. Operacji bez konsumenta nie dodajemy „na zapas” (martwa powierzchnia z rolą `SERVICE` i kontraktem
  do utrzymania).
- **Usunięcie operacji z `api/internal/patient.json` jest dla `oasdiff` zmianą łamiącą**, mimo że w repozytorium nie ma
  konsumenta. Krok `openapi-breaking` dla kontraktów wewnętrznych może więc zgłosić błąd w PR z tą zmianą (nie
  sprawdzone lokalnie: `oasdiff` działa tylko w CI). Takie usunięcie wymaga świadomego obejścia, a nie zmiany reguł.
- Użycie operacji tylko przy danych startowych (`ensure` w `scheduling`, `find` w `iam` jako sonda gotowości) też
  wymaga kontraktu, bo `SeedRunner` z nich korzysta.
- **Otwarte (FR-50):** rejestrator zakłada pacjenta bez konta, a konto powstaje później w `iam`. `iam` nie ma danych
  osobowych pacjenta, więc `ensure` (wymaga imienia, nazwiska i daty urodzenia) nie wystarcza do dopisania `userId`.
  Warianty: (A) wąska operacja po stronie `patient` (czyli powrót `linkAccount` razem z portem w `iam`), (B)
  zdarzenie `UserCreated` konsumowane przez `patient`, (C) bez `userId` w `patient` i `hasAccount` liczone przez `iam`.
  Decyzja zapada przy realizacji FR-50, a dodanie operacji jest zmianą addytywną, więc nie psuje kontraktu.
- Dług: port jest w pakiecie `patients.api` (reguła ArchUnit `modulesAreAccessedOnlyThroughApiOrApplication` zabrania
  sięgania do `application` i `infrastructure` cudzego modułu, a port jest używany także z `platform`); przeniesienie
  wymaga zmiany reguły. Brak testu kontraktowego dla klienta tokenów usługowych (`iam`, `POST /service-tokens`).
- Odrzucone: zostawienie `linkAccount` do czasu FR-50 (endpoint bez konsumenta i bez decyzji o wariancie); przeniesienie
  całego dopisywania konta do `iam` przez `ensure` z danymi pacjenta (`iam` nie ma tych danych); wspólny moduł z
  interfejsami usług (sprzężenie wersji i wdrożeń).
