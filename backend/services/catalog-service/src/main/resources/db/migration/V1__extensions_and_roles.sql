-- Rozszerzenia (zaufane od PostgreSQL 13, może je tworzyć właściciel bazy).
-- gen_random_uuid() jest wbudowane od PostgreSQL 13, więc pgcrypto nie jest potrzebne.
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS citext;

-- Rola aplikacji: bez uprawnień do tworzenia obiektów i bez omijania RLS.
-- Hasło z placeholdera Flyway (spring.flyway.placeholders.app_password).
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'p360_app') THEN
        CREATE ROLE p360_app LOGIN PASSWORD '${app_password}'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END
$$;

-- Dostęp do bazy zamknięty dla PUBLIC w skrypcie init; p360_app dostaje go jawnie.
DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO p360_app', current_database());
END
$$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO p360_app;

-- Nowe tabele i sekwencje tworzone przez właściciela są automatycznie dostępne dla aplikacji.
ALTER DEFAULT PRIVILEGES FOR ROLE p360_owner IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO p360_app;
ALTER DEFAULT PRIVILEGES FOR ROLE p360_owner IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO p360_app;
