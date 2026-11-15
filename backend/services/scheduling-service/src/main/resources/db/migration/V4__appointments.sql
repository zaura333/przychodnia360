-- Wizyty. Przedział [start, end) w tstzrange (ADR-B04). Kolizje pilnują ograniczenia wykluczające w bazie:
-- jeden wykonawca i jeden pacjent nie mogą mieć dwóch nakładających się aktywnych wizyt.
CREATE TABLE appointments (
    id                  uuid        PRIMARY KEY,
    tenant_id           uuid        NOT NULL,
    patient_id          uuid        NOT NULL,
    employee_id         uuid        NOT NULL REFERENCES employees (id),
    service_id          uuid        NOT NULL,
    time_range          tstzrange   NOT NULL CHECK (NOT isempty(time_range)),
    status              text        NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    booked_by_user_id   uuid        NOT NULL,
    cancelled_by_user_id uuid      ,
    cancelled_at        timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ex_appointments_employee
        EXCLUDE USING gist (tenant_id WITH =, employee_id WITH =, time_range WITH &&)
        WHERE (status IN ('SCHEDULED', 'COMPLETED')),
    CONSTRAINT ex_appointments_patient
        EXCLUDE USING gist (tenant_id WITH =, patient_id WITH =, time_range WITH &&)
        WHERE (status IN ('SCHEDULED', 'COMPLETED'))
);
CREATE INDEX ix_appt_employee_time ON appointments USING gist (tenant_id, employee_id, time_range);
CREATE INDEX ix_appt_patient ON appointments (tenant_id, patient_id, status);
CREATE INDEX ix_appt_status_start ON appointments (tenant_id, status, lower(time_range));

ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
ALTER TABLE appointments FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON appointments
    USING (tenant_id = app_tenant_id()) WITH CHECK (tenant_id = app_tenant_id());
