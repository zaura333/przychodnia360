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
GRANT EXECUTE ON FUNCTION resolve_tenant(text) TO p360_app;
