-- Kontekst placówki ustawiany w transakcji: SELECT set_config('app.tenant_id', '<uuid>', true).
-- Brak kontekstu daje NULL, a polityka tenant_id = app_tenant_id() nie zwraca wtedy żadnych wierszy.
-- Usługa nie ma tabeli placówek (należy do iam), więc nie ma też resolve_tenant ani zakresu platformy.
CREATE FUNCTION app_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid
$$;
