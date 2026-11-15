#!/bin/sh
# Wykonywany przez entrypoint obrazu postgres tylko przy pierwszym starcie (pusty wolumen).
# Tworzy rolę właściciela (Flyway) i bazy usług (każda usługa ma własną bazę, ADR-M05).
# Role p360_app, RLS i rozszerzenia należą do migracji Flyway każdej usługi, nie do tego skryptu.
# P360_DB_NAMES: lista baz rozdzielona przecinkami (np. p360_iam,p360_catalog,p360_scheduling);
# P360_DB_NAME: pojedyncza baza (zgodność wstecz, testy jednej usługi).
set -eu

: "${P360_OWNER_PASSWORD:?}"
DB_NAMES="${P360_DB_NAMES:-${P360_DB_NAME:?ustaw P360_DB_NAMES lub P360_DB_NAME}}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v owner_password="$P360_OWNER_PASSWORD" <<'SQL'
CREATE ROLE p360_owner LOGIN PASSWORD :'owner_password' NOSUPERUSER CREATEROLE NOBYPASSRLS;
SQL

for db in $(echo "$DB_NAMES" | tr ',' ' '); do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v db_name="$db" <<'SQL'
CREATE DATABASE :"db_name" OWNER p360_owner ENCODING 'UTF8';
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
SQL
done
