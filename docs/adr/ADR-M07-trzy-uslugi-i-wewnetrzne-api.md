# ADR-M07: Pierwsze trzy usługi (iam, catalog, scheduling) i wewnętrzne API

Status: przyjęte (2026-11-14; zlecenie właściciela: „najpierw trzy mikroserwisy, szybka ścieżka”). Świadome uproszczenia opisane niżej mają kryterium rewizji.

## Kontekst

Właściciel wybrał trzy mikroprocesy do pokazania jako osobne usługi: pacjent (rejestracja, usługi i ceny, wybór lekarza, rezerwacja, odwołanie), lekarz (własny grafik i wizyty) oraz administrator placówki (grafiki, usługi, pracownicy). Pełna ścieżka z planu (`plan-microservices.md`, fale 1, 4 i 5: JWKS, dane referencyjne ze zdarzeń, saga zakładania pracownika) to kilka tygodni pracy. Fala 0R przygotowała szwy: moduły rozmawiają przez fasady, nie ma kluczy obcych między modułami (ADR-M05), a tabela routingu ma jedną usługę na ścieżkę (ADR-M06).

## Decyzja

1. **Trzy usługi, osobne procesy i bazy** (jeden serwer PostgreSQL, bazy `p360_iam`, `p360_catalog`, `p360_scheduling`, każda z własnym łańcuchem migracji Flyway od V1):
   - `iam-service`: placówki, konta, logowanie i tokeny, pacjenci (moduły `identity`, `tenant`, `patients`),
   - `catalog-service`: usługi i kategorie z cenami (moduł `catalog`),
   - `scheduling-service`: pracownicy, grafiki, wizyty i sloty (moduły `staff`, `appointments`).
   Pacjenci zostają w iam do 1b (szyfrowanie), kiedy powstanie `patient-service`.
2. **Krawędź:** Traefik (`docker/traefik/dynamic.yml`) z regułami ścieżek z `routing-table.csv`; jedyny publiczny port backendu (8080). Frontend i kontrakt `api/openapi.json` bez zmian (kontrakt składany z kontraktów usług, `api/services/*.json`).
3. **Wewnętrzne API, synchroniczny REST.** scheduling-service woła `POST /internal/v1/services/lookup` (catalog) oraz `/internal/v1/patients/lookup`, `/internal/v1/employee-accounts`, `/internal/v1/users/{id}/employee-link` i `/internal/v1/users/{id}/deactivate` (iam). Fasady (`CatalogFacade`, `PatientFacade`, `IdentityFacade`) zostały, zmieniła się tylko ich implementacja (`HttpRemoteClients`), więc logika rezerwacji jest ta sama co w monolicie. Klient przekazuje token dostępu użytkownika (kontekst placówki i uprawnienia w drugiej usłudze z tego samego tokenu, RLS działa jak wcześniej) i nagłówek `X-P360-Internal-Token`. Błędy Problem Details są odtwarzane jako te same wyjątki domenowe (np. `email-taken` 409). Limit czasu 3 s, bez ponowień.
4. **Ochrona wewnętrznego API:** brak reguły na krawędzi (z zewnątrz 404), strażnik nagłówka usługowego przed Spring Security (bez nagłówka 404), a za nim zwykła weryfikacja tokenu i `@PreAuthorize` (bramka `PermissionMatrixTest` obejmuje także te endpointy).
5. **Tokeny:** wspólny klucz HS256 we wszystkich usługach; `JwtService`, filtr i `PermissionEvaluator` przeniesione do `p360-platform-starter`.
6. **Seed:** każda usługa seeduje własną bazę; identyfikatory deterministyczne (`DemoIds`) zgadzają się między usługami bez wywołań sieciowych przy starcie.

## Świadome uproszczenia (dług) i kryterium rewizji

| Uproszczenie | Skutek | Docelowo |
| ------------ | ------ | -------- |
| Wspólny sekret HS256 | każda usługa mogłaby wystawić token | RS256/EdDSA i JWKS z iam (ADR-M04, zadanie 1.2) przed wdrożeniem poza demo |
| Synchroniczne odczyty danych referencyjnych | awaria catalog lub iam blokuje rezerwację i widoki wizyt; dodatkowe wywołanie na każdą rezerwację i listę; rezerwacja trzyma połączenie z bazy (pula 5) w trakcie wywołań HTTP, więc wolna druga usługa może wyczerpać pulę | lokalne kopie `service_ref`, `patient_ref` zasilane zdarzeniami (outbox i SNS/SQS już są, ADR-M03) |
| Zakładanie pracownika: konto w iam, potem rekord w scheduling | błąd po utworzeniu konta zostawia konto bez pracownika (ponowienie z tym samym e-mailem daje 409) | saga z kompensacją lub zadanie uzgadniające (fala 5.1) |
| Odwołanie dostępu tylko w iam | po dezaktywacji konta token działa w catalog i scheduling do wygaśnięcia (do 10 minut) | zdarzenie `UserDeactivated` konsumowane przez każdą usługę (ADR-M04) |
| Jedna rola bazodanowa `p360_app`/`p360_owner` dla trzech baz | izolacja baz logiczna, nie przez osobne role | role per usługa (`<usługa>_app`) przy wdrożeniu AWS |
| Nagłówek usługowy zamiast tokenu usługowego | sekret współdzielony przez usługi | token usługowy (client credentials, ADR-M04) |

Rewizja: przed pierwszym wdrożeniem AWS albo gdy dojdzie czwarta usługa korzystająca z wewnętrznego API.

## Konsekwencje

- Testy catalog i scheduling nie potrzebują iam: tokeny wystawia lokalny `JwtService`, a w scheduling fasady zdalne zastępują atrapy (`RemoteFakes`) rozdzielone po placówkach. Prawdziwe HTTP sprawdza `HttpRemoteClientsTest`, kontrakt wewnętrzny iam `AccountFlowTest`, a całość e2e na trzech usługach za krawędzią.
- Każda usługa ma własne bramki: RLS, klasy retencji, granice kluczy obcych, ArchUnit, macierz uprawnień.
- Lokalnie trzy JVM zamiast jednej: więcej pamięci (limit 1 GB na usługę w compose).
