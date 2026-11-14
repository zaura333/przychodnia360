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
