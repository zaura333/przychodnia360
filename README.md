# Przychodnia360

Platforma webowa dla małych i średnich przychodni: rezerwacja wizyt, dokumentacja medyczna, recepty i skierowania (jako dokumenty wewnętrzne) oraz obsługa dokumentów z OCR.

> **Uwaga:** to projekt demonstracyjny. Wykorzystuje wyłącznie dane syntetyczne, nie łączy się z platformą P1 (usługi P1 są symulowane) i nie jest wyrobem medycznym. Nie wolno go używać z danymi rzeczywistych pacjentów.

## Status

Faza planowania, implementacja jeszcze się nie rozpoczęła. Dokumentacja projektu trafi do katalogu `docs/`.

## Technologie

Java 21 i Spring Boot, React z TypeScript, Tailwind CSS, PostgreSQL, AWS (Lambda, S3, CloudFront, RDS) oraz Terraform.

## Struktura repozytorium

Monorepo:

- `backend/` – backend i workery,
- `frontend/` – aplikacja webowa,
- `api/` – kontrakt OpenAPI,
- `infra/` – infrastruktura jako kod (Terraform),
- `docs/` – dokumentacja.

## CI i ochrona gałęzi `main`

Workflow w `.github/workflows/`: `backend.yml`, `frontend.yml` (zakres wyznacza zadanie `changes`; zbiorcze statusy `backend` i `frontend` raportują zawsze), `codeql.yml`, `gitleaks.yml`. Wersje narzędzi czytane z plików w repo: `.java-version`, `frontend/.nvmrc`. Dopóki szkielety backendu i frontendu nie istnieją, kroki budowania są pomijane z komunikatem.

**Ruleset `main`** (`.github/rulesets/main.json`): zakaz pushu bezpośredniego i usuwania, historia liniowa, PR bez wymaganych zatwierdzeń (jedna osoba; bramką są statusy CI), wymagane statusy `backend`, `frontend`, `gitleaks` oraz aktualność z `main`. Statusy `infra` i `e2e` dojdą z tymi workflow. Import: *Settings → Rules → Rulesets → New ruleset → Import a ruleset*.

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
