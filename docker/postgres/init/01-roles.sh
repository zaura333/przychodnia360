#!/bin/sh
# Wykonywany przez entrypoint obrazu postgres tylko przy pierwszym starcie (pusty wolumen).
# Tworzy rolę właściciela (Flyway) i bazę aplikacji. Role aplikacyjne (np. p360_app), RLS i rozszerzenia
# należą do migracji Flyway (V1+), nie do tego skryptu.
#
# 1) Baza główna (scheduling, dawniej clinic-core): P360_DB_NAME i P360_OWNER_PASSWORD, rola p360_owner (jak dotąd, w tym w testach).
# 2) Baza per usługa (zadanie 1.10): P360_SERVICE_DATABASES to lista nazw usług rozdzielona przecinkami, np.
#    "notification,integration-hub". Dla usługi <nazwa> powstają baza p360_<nazwa> i rola p360_<nazwa>_owner
#    z hasłem ze zmiennej P360_<NAZWA>_OWNER_PASSWORD (litery wielkie, "-" zamienione na "_"). Własności:
#    NOSUPERUSER, CREATEROLE (migracja V1 usługi tworzy jej rolę aplikacji), NOBYPASSRLS; CONNECT odebrany PUBLIC,
#    więc właściciel jednej usługi nie wejdzie do bazy innej.
set -eu

: "${P360_DB_NAME:?}"
: "${P360_OWNER_PASSWORD:?}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v owner_password="$P360_OWNER_PASSWORD" -v db_name="$P360_DB_NAME" <<'SQL'
CREATE ROLE p360_owner LOGIN PASSWORD :'owner_password' NOSUPERUSER CREATEROLE NOBYPASSRLS;
CREATE DATABASE :"db_name" OWNER p360_owner ENCODING 'UTF8';
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
SQL

for service in $(printf '%s' "${P360_SERVICE_DATABASES:-}" | tr ',' ' '); do
  case "$service" in
    [a-z]*) ;;
    *) echo "Niepoprawna nazwa usługi w P360_SERVICE_DATABASES: '$service'" >&2; exit 1 ;;
  esac
  if printf '%s' "$service" | grep -q '[^a-z0-9-]'; then
    echo "Niepoprawna nazwa usługi w P360_SERVICE_DATABASES: '$service' (dozwolone: a-z, 0-9, -)" >&2
    exit 1
  fi
  ident=$(printf '%s' "$service" | tr '-' '_')
  var="P360_$(printf '%s' "$ident" | tr 'a-z' 'A-Z')_OWNER_PASSWORD"
  password=$(printenv "$var" || true)
  if [ -z "$password" ]; then
    echo "Brak hasła właściciela bazy usługi '$service': ustaw $var" >&2
    exit 1
  fi
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v owner_role="p360_${ident}_owner" -v owner_password="$password" -v db_name="p360_${ident}" <<'SQL'
CREATE ROLE :"owner_role" LOGIN PASSWORD :'owner_password' NOSUPERUSER CREATEROLE NOBYPASSRLS;
CREATE DATABASE :"db_name" OWNER :"owner_role" ENCODING 'UTF8';
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
SQL
  echo "Baza usługi '$service': p360_${ident}, właściciel p360_${ident}_owner"
done
