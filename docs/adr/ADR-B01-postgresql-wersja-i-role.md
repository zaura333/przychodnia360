# ADR-B01: PostgreSQL: wersja i role bazodanowe

Status: przyjęte (etap 0 backendu, 2026-11-14)

Wersja PostgreSQL: 16.6

## Kontekst

Wersja bazy musi być taka sama lokalnie (docker-compose), w testach (Testcontainers) i w AWS (RDS), żeby rozjazd wersji nie ujawniał się dopiero na produkcji. Aplikacja ma łączyć się rolą, która nie omija izolacji placówek (RLS).

## Decyzja

**Wersja.** PostgreSQL 16 (obraz `postgres:16.6`). RDS ma używać tej samej wersji major (16); przy zmianie wersji zmieniamy ją jednocześnie w trzech miejscach: `docker-compose.yml`, `AbstractIntegrationTest.POSTGRES_IMAGE` i w linii `Wersja PostgreSQL:` tego ADR. Zgodność pilnuje `ComposeConsistencyTest`, więc zmiana tylko w jednym miejscu kończy się czerwonym testem.

**Role.**
- `p360_owner`: właściciel bazy i schematu, używany wyłącznie przez Flyway (migrator). Tworzony przez `docker/postgres/init/01-roles.sh` (`NOSUPERUSER`, `NOBYPASSRLS`, `CREATEROLE`). `CREATEROLE`, bo migracja V1 tworzy rolę aplikacji.
- `p360_app`: rola aplikacji, tworzona w migracji V1 (`NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOBYPASSRLS`, bez prawa tworzenia obiektów, nie jest właścicielem tabel). Dostaje `SELECT/INSERT/UPDATE/DELETE` na nowych tabelach przez `ALTER DEFAULT PRIVILEGES`.
- Każda tabela danych placówki ma `FORCE ROW LEVEL SECURITY`, więc polityki obowiązują także właściciela. Brak kontekstu placówki daje zero wierszy.
- Skrypt init tworzy tylko bazę i `p360_owner`; role, RLS i rozszerzenia są w migracjach Flyway (to samo źródło w Testcontainers i w compose).

## Konsekwencje

- W RDS rola właściciela potrzebuje uprawnienia `CREATEROLE` albo `p360_app` trzeba utworzyć w bootstrapie infrastruktury (do rozstrzygnięcia w CD2.1).
- Skrypt init działa tylko na pustym wolumenie; zmiana ról wymaga `docker compose down -v`.
- Hasła są lokalne i syntetyczne (`.env.example`); w AWS pochodzą z menedżera sekretów.

## Odrzucone opcje

- Jedna rola do migracji i aplikacji (aplikacja mogłaby zmieniać schemat i omijać RLS jako właściciel).
- `BYPASSRLS` dla aplikacji z filtrowaniem po stronie kodu (jeden błąd w zapytaniu to wyciek między placówkami).
- Wersja `latest` obrazu (niepowtarzalne środowisko).
