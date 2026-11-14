package pl.przychodnia360.tenant.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.tenant.application.TenantRepository;

@Repository
class JdbcTenantRepository implements TenantRepository {

    private final JdbcClient jdbc;

    JdbcTenantRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Funkcja SECURITY DEFINER z V3; działa bez kontekstu placówki. NULL (brak placówki) to pusty wynik. */
    @Override
    public Optional<UUID> resolveActiveSlug(String slug) {
        return jdbc.sql("SELECT id FROM (SELECT resolve_tenant(?) AS id) r WHERE id IS NOT NULL")
                .param(slug).query(UUID.class).optional();
    }

    @Override
    public Optional<String> findName(UUID tenantId) {
        return jdbc.sql("SELECT name FROM tenants WHERE id = ?").param(tenantId).query(String.class).optional();
    }
}
