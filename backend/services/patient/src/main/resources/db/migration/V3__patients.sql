-- Tabela pacjentów przeniesiona z clinic-core (V6); bez kluczy obcych do tenants i users (ADR-M05, należą do iam).
-- Pacjenci (minimalnie, bez PESEL, telefonu i adresu: te kolumny dochodzą w 1b już zaszyfrowane).
CREATE TABLE patients (
    id            uuid        PRIMARY KEY,
    tenant_id     uuid        NOT NULL,
    user_id       uuid        UNIQUE,
    first_name    text        NOT NULL CHECK (length(btrim(first_name)) > 0),
    last_name     text        NOT NULL CHECK (length(btrim(last_name)) > 0),
    birth_date    date        NOT NULL CHECK (birth_date <= current_date),
    contact_email citext,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0
);
CREATE INDEX ix_patients_name ON patients (tenant_id, lower(last_name), lower(first_name));

ALTER TABLE patients ENABLE ROW LEVEL SECURITY;
ALTER TABLE patients FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON patients
    USING (tenant_id = app_tenant_id()) WITH CHECK (tenant_id = app_tenant_id());

