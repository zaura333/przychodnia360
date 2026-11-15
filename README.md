# Przychodnia360

Platforma webowa dla małych i średnich przychodni: rezerwacja wizyt, dokumentacja medyczna, recepty i skierowania (jako dokumenty wewnętrzne) oraz obsługa dokumentów z OCR.

> **Uwaga:** to projekt demonstracyjny. Wykorzystuje wyłącznie dane syntetyczne, nie łączy się z platformą P1 (usługi P1 są symulowane) i nie jest wyrobem medycznym. Nie wolno go używać z danymi rzeczywistych pacjentów.

## Status

Etap 0 gotowy (szkielety, CI, Docker). Podetap 1a lokalnie: backend (logowanie, katalog, pracownicy z grafikami, rezerwacje z ochroną przed kolizjami, placówki platformy) i frontend (logowanie i rejestracja, kreator rezerwacji, terminarz rejestratora, panele administracji i platformy). Wdrożenie do AWS po etapie 1. Decyzje architektoniczne: `docs/adr/`.

## Technologie

Java 21 i Spring Boot, React z TypeScript, Tailwind CSS, PostgreSQL, AWS (ECS Fargate, S3, CloudFront, RDS) oraz Terraform.

## Struktura repozytorium

Monorepo:

- `backend/` – backend i workery,
- `frontend/` – aplikacja webowa,
- `api/` – kontrakt OpenAPI,
- `infra/` – infrastruktura jako kod (Terraform),
- `docs/` – dokumentacja.

## Backend: uruchomienie lokalne

Wymagania: Java 21, Docker. Pakiet bazowy: `pl.przychodnia360`. Moduły: `platform/p360-shared-core` (czysta Java), `platform/p360-platform-starter` (security, tenant/RLS, Problem Details, logowanie, czas) i `services/clinic-core` (monolit modułowy, Spring Boot).

```bash
cp .env.example .env                 # raz
docker compose up -d --wait          # postgres + mailpit
cd backend
./mvnw verify                        # testy (Testcontainers), generuje ../api/openapi.json
java -jar services/clinic-core/target/clinic-core.jar --spring.profiles.active=local
curl http://localhost:8080/api/v1/health
```

**Dane demo (profil `local`).** Przy starcie `SeedRunner` zakłada (idempotentnie) placówki `klinika-a` i `klinika-b` z administratorem, dwoma lekarzami, pielęgniarką, rejestratorem, trzema pacjentami, katalogiem, grafikami i wizytami. Konta mają adresy w domenie `<slug>.example.test`: `admin@`, `lekarz1@`, `lekarz2@`, `pielegniarka@`, `rejestracja@`, `pacjent1@` do `pacjent3@`; hasło wszystkich to wartość `SEED_PASSWORD` (lokalnie domyślnie `demo-przychodnia-2026`). Seed trwa kilka sekund po starcie. Wiadomości e-mail (linki ustawienia hasła) trafiają do Mailpit: http://localhost:8025. Przykład logowania: `POST /api/v1/auth/login` z `{"tenantSlug":"klinika-a","email":"rejestracja@klinika-a.example.test","password":"..."}`.

**Pierwszy administrator platformy** (bez migracji i bez hasła w repozytorium): jednorazowe uruchomienie z profilem `bootstrap-admin`, np. `BOOTSTRAP_ADMIN_EMAIL=root@platform.example.test BOOTSTRAP_ADMIN_PASSWORD=... java -jar services/clinic-core/target/clinic-core.jar --spring.profiles.active=local,bootstrap-admin`. Konto powstaje idempotentnie, po czym proces kończy pracę.

Po zmianie endpointów `./mvnw verify` aktualizuje eksport usługi (`api/exports/<usługa>.json`) i agregat `api/openapi.json` (składany z eksportów usług, ADR-M16); pliki trzeba zatwierdzić (CI sprawdza zgodność). Konwencje bazy: `docs/db-conventions.md`.

**Lista kontrolna: nowa tabela danych placówki**
- migracja `V<n>__opis.sql` z `tenant_id`, `ENABLE` i `FORCE ROW LEVEL SECURITY` oraz polityką `tenant_id = app_tenant_id()` (wzorzec w `docs/db-conventions.md`),
- `RlsCoverageTest` (podklasa `RlsCoverageContract` z `p360-test-support`) przechodzi (albo wyjątek z uzasadnieniem),
- test izolacji: placówka B nie widzi wierszy placówki A,
- brak `GRANT` dla `p360_app` poza domyślnymi uprawnieniami; funkcje `SECURITY DEFINER` tylko z uzasadnieniem.

**Lista kontrolna: nowy endpoint**
- `@PreAuthorize` odwołujące się do `PermissionPolicy` albo jawne `@PublicEndpoint` (domyślnie odmowa),
- wiersz w `PermissionMatrixTest` dla każdej roli (dozwolone i zabronione),
- błędy jako Problem Details (`/problems/<slug>`), cudzy zasób to `404`,
- zero danych osobowych w logach (PESEL maskowany),
- `./mvnw verify` i zatwierdzony `api/openapi.json`.

**Decyzje architektoniczne** są w `docs/adr/`: backend `ADR-B01` (PostgreSQL i role), `B02` (logowanie ze slugiem), `B03` (uprawnienia), `B04` (przedział czasu), `B05` (reguła 24 h), `B06` (Argon2id, wstępnie), `B07` (tokeny JWT i odświeżanie), `B08` (grafiki i ograniczenia wizyt); frontend `ADR-F01` do `F10` (`F08` rezerwacja i kolizje, `F09` terminarz rejestratora i odświeżanie, `F10` tabela tras i kontrakt API).

## Frontend: uruchomienie lokalne

Wymagania: Node z `frontend/.nvmrc`. Szczegóły, skrypty i zasady: `frontend/README.md`.

```bash
cd frontend
npm ci               # instaluje zależności i generuje klienta API z ../api/openapi.json
npm run dev          # http://localhost:5173, proxy /api na http://localhost:8080
npm test             # testy jednostkowe; npm run test:e2e dla Playwright
```

## Docker: środowisko lokalne

Docker służy do uruchamiania zależności (PostgreSQL, Mailpit) i do odtwarzania całej platformy lokalnie oraz w e2e. Obraz backendu jest też artefaktem wdrożeniowym (ECS Fargate, cel `from-jar`).

**Wymagania:** Docker Desktop z backendem WSL2 (Windows), około 4 GB pamięci dla WSL2 przy pełnym zestawie. Porty są wystawiane tylko na `127.0.0.1` i konfigurowalne w `.env`.

**Pierwsze uruchomienie:** `cp .env.example .env`. Po aktualizacji repozytorium porównaj `.env` z `.env.example` (compose wymaga haseł i odmówi startu, gdy brakuje zmiennej).

| Cel                                       | Polecenie                                            |
| ----------------------------------------- | ---------------------------------------------------- |
| Zależności (postgres, mailpit)            | `docker compose up -d --wait`                        |
| Rdzeń (backend w kontenerze + krawędź)    | `docker compose --profile core up -d --build --wait` |
| Wszystko (core + async: SNS i SQS)        | `docker compose --profile full up -d --build --wait` |
| Logi                                      | `docker compose logs -f backend`                     |
| Zatrzymanie (dane zostają)                | `docker compose --profile full down`                 |
| Reset danych i seed od nowa               | `docker compose --profile full down -v` a potem `up` |

**Profile compose** (łączone flagą `--profile`): `core` (usługi biznesowe, dziś monolit `clinic-core` jako `backend`, i krawędź Traefik na `http://127.0.0.1:8090`), `edge` (sama krawędź, gdy backend działa z IDE), `async` (LocalStack: SNS i SQS, obraz ok. 2 GB, poza `core`), `finance` (zarezerwowany dla billing i integration-hub, na razie bez usług), `full` (core, finance i async). Przy `down` podaj te same profile co przy `up`, inaczej kontenery profili zostaną. Frontend przez krawędź: `VITE_DEV_BACKEND=http://127.0.0.1:8090`.

**Baza per usługa:** lista usług w `P360_SERVICE_DATABASES` (np. `notification,integration-hub`) tworzy przy pierwszym starcie (pusty wolumen) bazę `p360_<usługa>` i właściciela `p360_<usługa>_owner` z hasłem z `P360_<USŁUGA>_OWNER_PASSWORD` (patrz `.env.example`); właściciel jednej usługi nie wchodzi do bazy innej. Baza główna `p360` (clinic-core) zostaje bez zmian.

Usługi i adresy: PostgreSQL `127.0.0.1:5432` (baza `p360`), Mailpit UI <http://localhost:8025> (SMTP `1025`), backend <http://localhost:8080/api/v1/health> (profile `core` i `full`). Domyślnie (`up -d`) startują tylko zależności, a backend działa z IDE lub z `java -jar`, frontend z `npm run dev`.

**Obraz backendu** (`backend/Dockerfile`): cel `runtime` (domyślny) buduje jar wewnątrz obrazu; cel `from-jar` kopiuje gotowy `backend/app.jar` (tak robi CI, więc e2e i wdrożenie uruchamiają ten sam jar). Obraz działa jako użytkownik inny niż root, na Alpine, x86_64.

**Wersja PostgreSQL** jest zapisana w trzech miejscach (`docker-compose.yml`, `PostgresIntegrationTest` w `p360-test-support`, `docs/adr/ADR-B01-postgresql-wersja-i-role.md`); `ComposeConsistencyTest` pilnuje, żeby się zgadzały.

**Rozwiązywanie problemów:**

- _Zajęty port 5432:_ ustaw `POSTGRES_PORT` w `.env` (lokalna instalacja PostgreSQL często go zajmuje); analogicznie `BACKEND_PORT`, `MAILPIT_UI_PORT`.
- _Kontener `postgres` nie startuje, błąd w skrypcie init:_ skrypt musi mieć końce linii LF (`.gitattributes` to wymusza dla `docker/**`); po poprawce `docker compose down -v` i start od nowa (skrypt init wykonuje się tylko na pustym wolumenie).
- _`required variable ... is missing`:_ uzupełnij `.env` brakującymi zmiennymi z `.env.example`.
- _Zmiana ról lub haseł bazy nie działa:_ skrypt init nie uruchamia się na istniejącym wolumenie; wykonaj `docker compose down -v`.
- _Backend w kontenerze jest wolny lub kończy pracę z powodu pamięci:_ limit to 0,5 vCPU i 2 GB (jak zadanie Fargate); zwiększ pamięć WSL2 w `%UserProfile%\.wslconfig`.
- _Docker Desktop nie odpowiada (`_ping`, błąd 500):_ zrestartuj Docker Desktop lub `wsl --shutdown`.

## CI i ochrona gałęzi `main`

Workflow w `.github/workflows/`: `backend.yml`, `frontend.yml` (zakres wyznacza zadanie `changes`; zbiorcze statusy `backend` i `frontend` raportują zawsze), `codeql.yml`, `gitleaks.yml`. Wersje narzędzi czytane z plików w repo: `.java-version`, `frontend/.nvmrc`.

**Przepływ gałęzi:** `feature/*` → PR do `develop` (squash) → PR `develop` → `main` (merge commit). Workflow CI uruchamiają się na każdym PR (dowolna gałąź bazowa) i na pushu do `main` i `develop`. Dependabot otwiera PR-y do `develop`.

**Rulesety** (`.github/rulesets/main.json` i `develop.json`): zakaz pushu bezpośredniego, force-pusha i usuwania, PR bez wymaganych zatwierdzeń (jedna osoba; bramką są statusy CI), wymagane statusy `backend`, `frontend`, `gitleaks`. Dla `develop` dodatkowo wymagana aktualność z gałęzią bazową; dla `main` nie (merge commity z `develop` powodowałyby, że `develop` ciągle jest "za" `main`). Brak wymogu liniowej historii na `main`, bo wydania to merge commity. Statusy `infra` i `e2e` dojdą z tymi workflow. Import: _Settings → Rules → Rulesets → New ruleset → Import a ruleset_ (osobno każdy plik).

**Ustawienia repozytorium (ręcznie, Settings):**

- Actions → General → Fork pull request workflows: wymagaj zatwierdzenia od wszystkich zewnętrznych współpracowników; domyślne uprawnienia `GITHUB_TOKEN`: tylko odczyt.
- Code security: włącz Dependabot alerts i security updates, secret scanning z push protection, private vulnerability reporting.
- Zakaz `pull_request_target` z checkoutem kodu z PR; brak self-hosted runnerów.

**Lokalnie:** `pre-commit install` włącza hook gitleaks (`.pre-commit-config.yaml`).

**Zasada migracji:** zgodne wstecz (expand/contract), skrypty Flyway niezmienne po scaleniu.

## Autorzy

Do uzupełnienia.

## Licencja

Do ustalenia.
