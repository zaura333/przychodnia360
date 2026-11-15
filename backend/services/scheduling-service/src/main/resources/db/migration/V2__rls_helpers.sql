-- Kontekst placówki ustawiany w transakcji: SELECT set_config('app.tenant_id', '<uuid>', true).
-- Brak kontekstu daje NULL, a polityka tenant_id = app_tenant_id() nie zwraca wtedy żadnych wierszy.
CREATE FUNCTION app_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid
$$;
