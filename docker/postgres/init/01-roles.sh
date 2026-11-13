#!/bin/sh
# Wykonywany przez entrypoint obrazu postgres tylko przy pierwszym starcie (pusty wolumen).
# Tworzy rolę właściciela (Flyway) i bazę aplikacji. Role p360_app, RLS i rozszerzenia
# należą do migracji Flyway (V1+), nie do tego skryptu.
set -eu

: "${P360_DB_NAME:?}"
: "${P360_OWNER_PASSWORD:?}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v owner_password="$P360_OWNER_PASSWORD" -v db_name="$P360_DB_NAME" <<'SQL'
CREATE ROLE p360_owner LOGIN PASSWORD :'owner_password' NOSUPERUSER NOCREATEROLE NOBYPASSRLS;
CREATE DATABASE :"db_name" OWNER p360_owner ENCODING 'UTF8';
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
SQL
