-- Fala 1.3 (ADR-M03): transakcyjny outbox i inbox idempotencji. Wzorzec do skopiowania do każdej usługi
-- (docs/db-conventions.md, sekcja Outbox/inbox).
--
-- outbox: zdarzenie zapisywane w transakcji biznesowej; przekaźnik publikuje je do brokera. Zwykły dostęp ma
-- polityka tenant_isolation, a przekaźnik (wiersze wszystkich placówek) osobne ustawienie app.relay = 'on'.
-- Nie używa app_scope() (zakres platformy), bo tego zakresu nie mogą znać tabele danych placówek (RlsCoverageTest).
CREATE FUNCTION app_relay() RETURNS boolean
    LANGUAGE sql STABLE
AS $$
    SELECT COALESCE(current_setting('app.relay', true), '') = 'on'
$$;

CREATE TABLE outbox (
    id             uuid        PRIMARY KEY,
    tenant_id      uuid        NOT NULL,
    aggregate_id   uuid        NOT NULL,
    type           text        NOT NULL,
    schema_version int         NOT NULL DEFAULT 1,
    payload        jsonb       NOT NULL,
    occurred_at    timestamptz NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    published_at   timestamptz,
    attempts       int         NOT NULL DEFAULT 0,
    last_error     text
);
CREATE INDEX ix_outbox_pending ON outbox (created_at, id) WHERE published_at IS NULL;
CREATE INDEX ix_outbox_published ON outbox (published_at) WHERE published_at IS NOT NULL;

ALTER TABLE outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON outbox
    USING (tenant_id = app_tenant_id())
    WITH CHECK (tenant_id = app_tenant_id());
CREATE POLICY relay_scope ON outbox
    USING (app_relay())
    WITH CHECK (app_relay());

-- processed_events: konsument zapisuje (consumer, event_id) w transakcji obsługi; duplikat dostawy jest pomijany.
CREATE TABLE processed_events (
    consumer     text        NOT NULL,
    event_id     uuid        NOT NULL,
    tenant_id    uuid        NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);
CREATE INDEX ix_processed_events_time ON processed_events (processed_at);

ALTER TABLE processed_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE processed_events FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON processed_events
    USING (tenant_id = app_tenant_id())
    WITH CHECK (tenant_id = app_tenant_id());
