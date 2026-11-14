-- Tabela wzorcowa wyłącznie dla testów: wzorzec RLS, który mają powielać tabele danych placówki.
CREATE TABLE _tenant_probe (
    id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid NOT NULL,
    payload   text NOT NULL
);

ALTER TABLE _tenant_probe ENABLE ROW LEVEL SECURITY;
ALTER TABLE _tenant_probe FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON _tenant_probe
    USING (tenant_id = app_tenant_id())
    WITH CHECK (tenant_id = app_tenant_id());
