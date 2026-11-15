-- Katalog usług placówki: kategorie i usługi (usuwanie logiczne przez flagę active).
CREATE TABLE service_categories (
    id         uuid        PRIMARY KEY,
    tenant_id  uuid        NOT NULL REFERENCES tenants (id),
    name       text        NOT NULL CHECK (length(btrim(name)) > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_service_categories_name ON service_categories (tenant_id, lower(name));

CREATE TABLE services (
    id               uuid          PRIMARY KEY,
    tenant_id        uuid          NOT NULL REFERENCES tenants (id),
    category_id      uuid          NOT NULL REFERENCES service_categories (id),
    name             text          NOT NULL CHECK (length(btrim(name)) > 0),
    description      text,
    price            numeric(10,2) NOT NULL CHECK (price >= 0),
    duration_minutes int           NOT NULL CHECK (duration_minutes BETWEEN 5 AND 480),
    performer_type   text          NOT NULL CHECK (performer_type IN ('DOCTOR', 'MEDICAL_STAFF')),
    active           boolean       NOT NULL DEFAULT true,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    version          bigint        NOT NULL DEFAULT 0
);
CREATE INDEX ix_services_filter ON services (tenant_id, active, category_id, price);

ALTER TABLE service_categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_categories FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON service_categories
    USING (tenant_id = app_tenant_id()) WITH CHECK (tenant_id = app_tenant_id());

ALTER TABLE services ENABLE ROW LEVEL SECURITY;
ALTER TABLE services FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON services
    USING (tenant_id = app_tenant_id()) WITH CHECK (tenant_id = app_tenant_id());
