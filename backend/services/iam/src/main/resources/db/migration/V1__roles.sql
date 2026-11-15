-- Rozszerzenie dla kolumny users.email (zaufane od PostgreSQL 13, może je tworzyć właściciel bazy).
CREATE EXTENSION IF NOT EXISTS citext;

-- Baza usługi iam (S3): rola aplikacji i uprawnienia domyślne. Nazwy ról i hasło z placeholderów
-- Flyway (spring.flyway.placeholders.*), bo w każdej bazie usługi role są osobne (p360_<usługa>_owner / _app).
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${app_role}') THEN
        CREATE ROLE ${app_role} LOGIN PASSWORD '${app_password}'
            NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END
$$;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO ${app_role}', current_database());
END
$$;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO ${app_role};

ALTER DEFAULT PRIVILEGES FOR ROLE ${owner_role} IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${app_role};
ALTER DEFAULT PRIVILEGES FOR ROLE ${owner_role} IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO ${app_role};
