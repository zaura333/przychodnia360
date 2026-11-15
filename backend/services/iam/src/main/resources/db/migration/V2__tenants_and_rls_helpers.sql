-- iam (S3): placówki i funkcje kontekstu RLS przeniesione z clinic-core (V2, V3). Tabela placówek należy wyłącznie
-- do iam; pozostałe usługi znają tylko tenant_id z tokenu (ADR-M05).
-- Placówki. Tabela nie ma kolumny tenant_id (sama jest słownikiem placówek), więc nie podlega RLS.
-- Lista aktywnych placówek jest publiczna (GET /api/v1/facilities).
CREATE TABLE tenants (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    slug       text        NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}$'),
    name       text        NOT NULL CHECK (length(btrim(name)) > 0),
    active     boolean     NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- Kontekst placówki ustawiany w transakcji: SELECT set_config('app.tenant_id', '<uuid>', true).
-- Brak kontekstu daje NULL, a polityka tenant_id = app_tenant_id() nie zwraca wtedy żadnych wierszy.
CREATE FUNCTION app_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid
$$;

-- Logowanie zna tylko slug placówki, więc rozwiązuje go funkcją SECURITY DEFINER.
CREATE FUNCTION resolve_tenant(p_slug text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path = public, pg_temp
AS $$
    SELECT id FROM tenants WHERE slug = p_slug AND active
$$;

REVOKE ALL ON FUNCTION resolve_tenant(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION resolve_tenant(text) TO ${app_role};
