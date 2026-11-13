# Przychodnia360

Platforma webowa dla małych i średnich przychodni: rezerwacja wizyt, dokumentacja medyczna, recepty i skierowania (jako dokumenty wewnętrzne) oraz obsługa dokumentów z OCR.

> **Uwaga:** to projekt demonstracyjny. Wykorzystuje wyłącznie dane syntetyczne, nie łączy się z platformą P1 (usługi P1 są symulowane) i nie jest wyrobem medycznym. Nie wolno go używać z danymi rzeczywistych pacjentów.

## Status

Etap 0 w toku: gotowe szkielety backendu (Spring Boot, Flyway, RLS, testy) i frontendu (React, Vite, testy, CSP), obraz Dockera backendu, prototyp terminarza. Decyzje architektoniczne: `docs/adr/`.

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

Wymagania: Java 21, Docker. Pakiet bazowy: `pl.przychodnia360`. Moduły: `shared-core` (czysta Java) i `backend-api` (Spring Boot).

```bash
cp .env.example .env                 # raz
docker compose up -d --wait          # postgres + mailpit
cd backend
./mvnw verify                        # testy (Testcontainers), generuje ../api/openapi.json
java -jar backend-api/target/backend-api.jar --spring.profiles.active=local
curl http://localhost:8080/api/v1/health
```

Po zmianie endpointów `./mvnw verify` aktualizuje `api/openapi.json`; plik trzeba zatwierdzić (CI sprawdza zgodność). Konwencje bazy: `docs/db-conventions.md`.

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
| Cała platforma (z backendem w kontenerze) | `docker compose --profile full up -d --build --wait` |
| Logi                                      | `docker compose logs -f backend`                     |
| Zatrzymanie (dane zostają)                | `docker compose --profile full down`                 |
| Reset danych i seed od nowa               | `docker compose --profile full down -v` a potem `up` |

Usługi i adresy: PostgreSQL `127.0.0.1:5432` (baza `p360`), Mailpit UI <http://localhost:8025> (SMTP `1025`), backend <http://localhost:8080/api/v1/health> (profil `full`). Domyślnie (`up -d`) startują tylko zależności, a backend działa z IDE lub z `java -jar`, frontend z `npm run dev`.

**Obraz backendu** (`backend/Dockerfile`): cel `runtime` (domyślny) buduje jar wewnątrz obrazu; cel `from-jar` kopiuje gotowy `backend/app.jar` (tak robi CI, więc e2e i wdrożenie uruchamiają ten sam jar). Obraz działa jako użytkownik inny niż root, na Alpine, x86_64.

**Wersja PostgreSQL** jest zapisana w trzech miejscach (`docker-compose.yml`, `AbstractIntegrationTest`, `docs/adr/ADR-B01-postgresql-wersja-i-role.md`); `ComposeConsistencyTest` pilnuje, żeby się zgadzały.

**Rozwiązywanie problemów:**

- _Zajęty port 5432:_ ustaw `POSTGRES_PORT` w `.env` (lokalna instalacja PostgreSQL często go zajmuje); analogicznie `BACKEND_PORT`, `MAILPIT_UI_PORT`.
- _Kontener `postgres` nie startuje, błąd w skrypcie init:_ skrypt musi mieć końce linii LF (`.gitattributes` to wymusza dla `docker/**`); po poprawce `docker compose down -v` i start od nowa (skrypt init wykonuje się tylko na pustym wolumenie).
- _`required variable ... is missing`:_ uzupełnij `.env` brakującymi zmiennymi z `.env.example`.
- _Zmiana ról lub haseł bazy nie działa:_ skrypt init nie uruchamia się na istniejącym wolumenie; wykonaj `docker compose down -v`.
- _Backend w kontenerze jest wolny lub kończy pracę z powodu pamięci:_ limit to 0,5 vCPU i 2 GB (jak zadanie Fargate); zwiększ pamięć WSL2 w `%UserProfile%\.wslconfig`.
- _Docker Desktop nie odpowiada (`_ping`, błąd 500):_ zrestartuj Docker Desktop lub `wsl --shutdown`.

## CI i ochrona gałęzi `main`

Workflow w `.github/workflows/`: `backend.yml`, `frontend.yml` (zakres wyznacza zadanie `changes`; zbiorcze statusy `backend` i `frontend` raportują zawsze), `codeql.yml`, `gitleaks.yml`. Wersje narzędzi czytane z plików w repo: `.java-version`, `frontend/.nvmrc`.

**Ruleset `main`** (`.github/rulesets/main.json`): zakaz pushu bezpośredniego i usuwania, historia liniowa, PR bez wymaganych zatwierdzeń (jedna osoba; bramką są statusy CI), wymagane statusy `backend`, `frontend`, `gitleaks` oraz aktualność z `main`. Statusy `infra` i `e2e` dojdą z tymi workflow. Import: _Settings → Rules → Rulesets → New ruleset → Import a ruleset_.

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
