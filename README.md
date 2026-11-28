# Przychodnia360

Platforma webowa dla małych i średnich przychodni: rezerwacja wizyt, dokumentacja medyczna, recepty i skierowania (jako dokumenty wewnętrzne) oraz obsługa dokumentów z OCR.

> [!WARNING]
> To projekt demonstracyjny. Wykorzystuje wyłącznie dane syntetyczne, nie łączy się z platformą P1 (usługi P1 są symulowane) i nie jest wyrobem medycznym. Nie wolno go używać z danymi rzeczywistych pacjentów.

## Spis treści

- [Status](#status)
- [Technologie](#technologie)
- [Struktura repozytorium](#struktura-repozytorium)
- [Quickstart](#quickstart)
- [Backend](#backend-uruchomienie-lokalne)
- [Frontend](#frontend-uruchomienie-lokalne)
- [Docker](#docker-środowisko-lokalne)
- [CI i ochrona gałęzi `main`](#ci-i-ochrona-gałęzi-main)
- [Autorzy](#autorzy)
- [Licencja](#licencja)

## Status

- **Gotowe:**
  - etap 0: szkielety, CI, Docker,
  - podetap 1a (wizyty):
    - backend: logowanie, katalog, pracownicy z grafikami, rezerwacje z ochroną przed kolizjami, placówki platformy,
    - frontend: logowanie i rejestracja, kreator rezerwacji, terminarz rejestratora, panele administracji i platformy,
  - etap 0M: platforma mikroserwisów, rdzeń rozbity na usługi `iam`, `catalog`, `patient`, `scheduling`.
- **Następny:** podetap 1b (dokumentacja medyczna), jeszcze niezaczęty.
- **Dalej:** wdrożenie do AWS po etapie 1.

Pełny plan etapów (1a–1d, 2–5, etapy opcjonalne): [`docs/roadmap.md`](docs/roadmap.md). Decyzje architektoniczne: [`docs/adr/`](docs/adr/).

## Technologie

| Warstwa        | Technologie                                    |
| -------------- | ---------------------------------------------- |
| Backend        | Java 21, Spring Boot                           |
| Frontend       | React, TypeScript, Tailwind CSS                |
| Baza danych    | PostgreSQL                                     |
| Chmura         | AWS (ECS Fargate, S3, CloudFront, RDS)         |
| Infrastruktura | Terraform                                      |

## Struktura repozytorium

Monorepo:

| Katalog     | Zawartość                           |
| ----------- | ----------------------------------- |
| `backend/`  | backend i workery                   |
| `frontend/` | aplikacja webowa                    |
| `api/`      | kontrakt OpenAPI                    |
| `infra/`    | infrastruktura jako kod (Terraform) |
| `docs/`     | dokumentacja                        |

## Quickstart

Najszybsza droga do działającej aplikacji lokalnie: backend w Dockerze, frontend z Vite.

**Wymagania:** Docker (Docker Desktop z WSL2 na Windows) oraz Node z `frontend/.nvmrc`. Java 21 jest potrzebna tylko do pracy nad backendem.

### 1. Konfiguracja

```bash
git clone <adres-repozytorium>
cd przychodnia360
cp .env.example .env        # PowerShell: Copy-Item .env.example .env
```

### 2. Backend (Docker)

```bash
docker compose --profile core up -d --build --wait
```

Startuje PostgreSQL, Mailpit, usługi backendu i krawędź Traefik na `http://127.0.0.1:8090`. Dane demo pojawiają się kilka sekund po starcie.

### 3. Frontend

```bash
cd frontend
npm ci
npm run dev
```

### 4. Logowanie

Otwórz <http://localhost:5173> i zaloguj się kontem demo:

| Pole     | Wartość                                                     |
| -------- | ----------------------------------------------------------- |
| Placówka | `klinika-a`                                                 |
| E-mail   | `rejestracja@klinika-a.example.test`                        |
| Hasło    | wartość `SEED_PASSWORD` (domyślnie `demo-przychodnia-2026`) |

Inne konta opisuje sekcja [Dane demo](#dane-demo).

### Przydatne adresy

| Usługa                            | Adres                                 |
| --------------------------------- | ------------------------------------- |
| Aplikacja (frontend)              | <http://localhost:5173>               |
| Mailpit (e-maile, linki do hasła) | <http://localhost:8025>               |
| Health backendu                   | <http://localhost:8080/api/v1/health> |

### Zatrzymanie

```bash
docker compose --profile core down        # dane zostają
docker compose --profile core down -v     # kasuje też dane (seed od nowa)
```

> [!TIP]
> Zajęte porty, brakujące zmienne w `.env` i inne kłopoty: zobacz [Rozwiązywanie problemów](#rozwiązywanie-problemów).

## Backend: uruchomienie lokalne

**Wymagania:** Java 21, Docker. Zależności (PostgreSQL, Mailpit) i całość platformy uruchomisz według [Quickstartu](#quickstart); z IDE każda usługa działa osobno.

```bash
cd backend
./mvnw verify                        # testy (Testcontainers), generuje ../api/openapi.json
java -jar services/scheduling/target/scheduling.jar --spring.profiles.active=local
```

### Dane demo

Profil `local`. Przy starcie `SeedRunner` zakłada (idempotentnie) placówki `klinika-a` i `klinika-b` z administratorem, dwoma lekarzami, pielęgniarką, rejestratorem, trzema pacjentami, katalogiem, grafikami i wizytami.

Konta mają adresy w domenie `<slug>.example.test`; hasło wszystkich to `SEED_PASSWORD`.

| Rola          | Adres (przed `@<slug>.example.test`) |
| ------------- | ------------------------------------ |
| Administrator | `admin`                              |
| Lekarze       | `lekarz1`, `lekarz2`                 |
| Pielęgniarka  | `pielegniarka`                       |
| Rejestrator   | `rejestracja`                        |
| Pacjenci      | `pacjent1` do `pacjent3`             |

### Pierwszy administrator platformy

Bez migracji i bez hasła w repozytorium: jednorazowe uruchomienie z profilem `bootstrap-admin`.

```bash
BOOTSTRAP_ADMIN_EMAIL=root@platform.example.test \
BOOTSTRAP_ADMIN_PASSWORD=... \
java -jar services/iam/target/iam.jar --spring.profiles.active=local,bootstrap-admin
```

Konto powstaje idempotentnie, po czym proces kończy pracę.

### Dokumentacja pomocnicza

- konwencje bazy: [`docs/db-conventions.md`](docs/db-conventions.md),
- decyzje architektoniczne: [`docs/adr/`](docs/adr/).

## Frontend: uruchomienie lokalne

**Wymagania:** Node z `frontend/.nvmrc`. Uruchomienie opisuje [Quickstart](#quickstart), a skrypty i zasady [`frontend/README.md`](frontend/README.md).

## Docker: środowisko lokalne

Docker służy do uruchamiania zależności (PostgreSQL, Mailpit) i do odtwarzania całej platformy lokalnie oraz w e2e. Obraz backendu jest też artefaktem wdrożeniowym (ECS Fargate, cel `from-jar`).

**Wymagania:** Docker Desktop z backendem WSL2 (Windows), około 4 GB pamięci dla WSL2 przy pełnym zestawie. Porty są wystawiane tylko na `127.0.0.1` i konfigurowalne w `.env`.

**Po aktualizacji repozytorium:** porównaj `.env` z `.env.example` (compose wymaga haseł i odmówi startu, gdy brakuje zmiennej).

### Polecenia

| Cel                                    | Polecenie                                            |
| -------------------------------------- | ---------------------------------------------------- |
| Zależności (postgres, mailpit)         | `docker compose up -d --wait`                        |
| Rdzeń (backend w kontenerze + krawędź) | `docker compose --profile core up -d --build --wait` |
| Wszystko (core + async: SNS i SQS)     | `docker compose --profile full up -d --build --wait` |
| Logi                                   | `docker compose logs -f backend`                     |
| Zatrzymanie (dane zostają)             | `docker compose --profile full down`                 |
| Reset danych i seed od nowa            | `docker compose --profile full down -v`, potem `up`  |

Domyślnie (`up -d`) startują tylko zależności (PostgreSQL `127.0.0.1:5432`, baza `p360`; SMTP Mailpit `1025`), a backend działa z IDE lub z `java -jar`.

### Profile compose

Łączone flagą `--profile`.

| Profil    | Zawartość                                                                                  |
| --------- | ------------------------------------------------------------------------------------------ |
| `core`    | usługi biznesowe `iam`, `catalog`, `patient`, `scheduling` i krawędź Traefik (`:8090`)     |
| `edge`    | sama krawędź, gdy backend działa z IDE                                                     |
| `async`   | LocalStack (SNS i SQS), obraz ok. 2 GB, poza `core`                                        |
| `finance` | zarezerwowany dla billing i integration-hub, na razie bez usług                            |
| `full`    | `core`, `finance` i `async`                                                                |

> [!NOTE]
> Przy `down` podaj te same profile co przy `up`, inaczej kontenery profili zostaną.

### Baza per usługa

Lista usług w `P360_SERVICE_DATABASES` (np. `notification,integration-hub`) tworzy przy pierwszym starcie (pusty wolumen):

- bazę `p360_<usługa>`,
- właściciela `p360_<usługa>_owner` z hasłem z `P360_<USŁUGA>_OWNER_PASSWORD` (patrz `.env.example`).

Właściciel jednej usługi nie wchodzi do bazy innej. Baza główna `p360` należy do `scheduling` (łańcuch migracji zachowany, ADR-M12); domyślna lista to `catalog,patient,iam`.

### Obraz backendu

`backend/Dockerfile`:

- cel `runtime` (domyślny) buduje jar wewnątrz obrazu,
- cel `from-jar` kopiuje gotowy `backend/app.jar` (tak robi CI, więc e2e i wdrożenie uruchamiają ten sam jar).

Obraz działa jako użytkownik inny niż root, na Alpine, x86_64.

### Rozwiązywanie problemów

| Objaw                                                    | Rozwiązanie                                                                                                                                                                          |
| -------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Zajęty port 5432                                         | Ustaw `POSTGRES_PORT` w `.env` (lokalna instalacja PostgreSQL często go zajmuje); analogicznie `SCHEDULING_PORT`, `IAM_PORT`, `MAILPIT_UI_PORT`.                                    |
| Kontener `postgres` nie startuje, błąd w skrypcie init   | Skrypt musi mieć końce linii LF (`.gitattributes` to wymusza dla `docker/**`). Po poprawce `docker compose down -v` i start od nowa (init wykonuje się tylko na pustym wolumenie).   |
| `required variable ... is missing`                       | Uzupełnij `.env` brakującymi zmiennymi z `.env.example`.                                                                                                                             |
| Zmiana ról lub haseł bazy nie działa                     | Skrypt init nie uruchamia się na istniejącym wolumenie; wykonaj `docker compose down -v`.                                                                                            |
| Backend w kontenerze jest wolny lub kończy pracę (pamięć) | Limit to 0,5 vCPU i 2 GB (jak zadanie Fargate); zwiększ pamięć WSL2 w `%UserProfile%\.wslconfig`.                                                                                    |
| Docker Desktop nie odpowiada (`_ping`, błąd 500)         | Zrestartuj Docker Desktop lub wykonaj `wsl --shutdown`.                                                                                                                              |

## CI i ochrona gałęzi `main`

Workflow w `.github/workflows/`:

- `backend.yml`,
- `frontend.yml` (zakres wyznacza zadanie `changes`; zbiorcze statusy `backend` i `frontend` raportują zawsze),
- `codeql.yml`,
- `gitleaks.yml`.

Wersje narzędzi czytane z plików w repo: `.java-version`, `frontend/.nvmrc`.

**Przepływ gałęzi:** `feature/*` → PR do `develop` (squash) → PR `develop` → `main` (merge commit). Workflow CI uruchamiają się na każdym PR (dowolna gałąź bazowa) i na pushu do `main` i `develop`. Dependabot otwiera PR-y do `develop`.

**Lokalnie:** `pre-commit install` włącza hook gitleaks (`.pre-commit-config.yaml`).

**Zasada migracji:** zgodne wstecz (expand/contract), skrypty Flyway niezmienne po scaleniu.

## Autorzy

Do uzupełnienia.

## Licencja

Do ustalenia.
