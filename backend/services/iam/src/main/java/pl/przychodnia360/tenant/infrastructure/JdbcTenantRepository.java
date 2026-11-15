package pl.przychodnia360.tenant.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.tenant.api.TenantFacade.TenantInfo;
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

    @Override
    public Optional<TenantInfo> findInfo(UUID tenantId) {
        return jdbc.sql("SELECT id, slug, name, active FROM tenants WHERE id = ?").param(tenantId)
                .query((rs, i) -> info(rs)).optional();
    }

    @Override
    public List<TenantInfo> findAll() {
        return jdbc.sql("SELECT id, slug, name, active FROM tenants ORDER BY lower(name), id")
                .query((rs, i) -> info(rs)).list();
    }

    @Override
    public void insert(TenantInfo t) {
        try {
            jdbc.sql("INSERT INTO tenants (id, slug, name, active) VALUES (?, ?, ?, ?)")
                    .params(t.id(), t.slug(), t.name(), t.active()).update();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("slug-taken", "Placówka o tym identyfikatorze już istnieje.");
        }
    }

    @Override
    public void update(TenantInfo t) {
        jdbc.sql("UPDATE tenants SET name = ?, active = ?, updated_at = now() WHERE id = ?")
                .params(t.name(), t.active(), t.id()).update();
    }

    private static TenantInfo info(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TenantInfo(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                rs.getBoolean("active"));
    }
}
