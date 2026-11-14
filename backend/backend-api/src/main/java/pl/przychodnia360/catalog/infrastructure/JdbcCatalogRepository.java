package pl.przychodnia360.catalog.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.catalog.api.PerformerType;
import pl.przychodnia360.catalog.application.CatalogRepository;
import pl.przychodnia360.catalog.domain.MedicalService;
import pl.przychodnia360.catalog.domain.ServiceCategory;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.TenantContext;

@Repository
class JdbcCatalogRepository implements CatalogRepository {

    private static final String SERVICE_COLUMNS =
            "id, category_id, name, description, price, duration_minutes, performer_type, active, version";

    private final JdbcClient jdbc;

    JdbcCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Warunki filtra; wartości zawsze jako parametry, fragmenty SQL są stałe. */
    private static String where(ServiceFilter f, List<Object> params) {
        StringBuilder sql = new StringBuilder(" WHERE active");
        if (f.categoryId() != null) {
            sql.append(" AND category_id = ?");
            params.add(f.categoryId());
        }
        if (f.minPrice() != null) {
            sql.append(" AND price >= ?");
            params.add(f.minPrice());
        }
        if (f.maxPrice() != null) {
            sql.append(" AND price <= ?");
            params.add(f.maxPrice());
        }
        if (f.performerType() != null) {
            sql.append(" AND performer_type = ?");
            params.add(f.performerType().name());
        }
        return sql.toString();
    }

    @Override
    public List<MedicalService> findServices(ServiceFilter filter, int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT " + SERVICE_COLUMNS + " FROM services" + where(filter, params)
                + " ORDER BY lower(name), id LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return jdbc.sql(sql).params(params).query(JdbcCatalogRepository::service).list();
    }

    @Override
    public long countServices(ServiceFilter filter) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT count(*) FROM services" + where(filter, params);
        return jdbc.sql(sql).params(params).query(Long.class).single();
    }

    @Override
    public Optional<MedicalService> findService(UUID id) {
        return jdbc.sql("SELECT " + SERVICE_COLUMNS + " FROM services WHERE id = ?").param(id)
                .query(JdbcCatalogRepository::service).optional();
    }

    @Override
    public void insertService(MedicalService s) {
        jdbc.sql("""
                INSERT INTO services (id, tenant_id, category_id, name, description, price, duration_minutes,
                                      performer_type, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(s.id(), TenantContext.requireTenant().value(), s.categoryId(), s.name(), s.description(),
                        s.price(), s.durationMinutes(), s.performerType().name(), s.active())
                .update();
    }

    @Override
    public void updateService(MedicalService s) {
        int rows = jdbc.sql("""
                UPDATE services SET category_id = ?, name = ?, description = ?, price = ?, duration_minutes = ?,
                       performer_type = ?, active = ?, updated_at = now(), version = version + 1
                 WHERE id = ? AND version = ?
                """)
                .params(s.categoryId(), s.name(), s.description(), s.price(), s.durationMinutes(),
                        s.performerType().name(), s.active(), s.id(), s.version())
                .update();
        if (rows == 0) {
            throw new ConflictException("concurrent-update", "Usługa została zmieniona równolegle. Odśwież i spróbuj ponownie.");
        }
    }

    @Override
    public List<ServiceCategory> findCategories() {
        return jdbc.sql("SELECT id, name, version FROM service_categories ORDER BY lower(name)")
                .query(JdbcCatalogRepository::category).list();
    }

    @Override
    public Optional<ServiceCategory> findCategory(UUID id) {
        return jdbc.sql("SELECT id, name, version FROM service_categories WHERE id = ?").param(id)
                .query(JdbcCatalogRepository::category).optional();
    }

    @Override
    public void insertCategory(ServiceCategory c) {
        try {
            jdbc.sql("INSERT INTO service_categories (id, tenant_id, name) VALUES (?, ?, ?)")
                    .params(c.id(), TenantContext.requireTenant().value(), c.name()).update();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("category-exists", "Kategoria o tej nazwie już istnieje.");
        }
    }

    @Override
    public void updateCategory(ServiceCategory c) {
        try {
            int rows = jdbc.sql("UPDATE service_categories SET name = ?, updated_at = now(), version = version + 1 "
                    + "WHERE id = ? AND version = ?").params(c.name(), c.id(), c.version()).update();
            if (rows == 0) {
                throw new ConflictException("concurrent-update", "Kategoria została zmieniona równolegle.");
            }
        } catch (DuplicateKeyException e) {
            throw new ConflictException("category-exists", "Kategoria o tej nazwie już istnieje.");
        }
    }

    @Override
    public boolean deleteCategory(UUID id) {
        return jdbc.sql("DELETE FROM service_categories WHERE id = ?").param(id).update() > 0;
    }

    @Override
    public long countActiveServicesInCategory(UUID categoryId) {
        return jdbc.sql("SELECT count(*) FROM services WHERE category_id = ? AND active").param(categoryId)
                .query(Long.class).single();
    }

    private static MedicalService service(ResultSet rs, int row) throws SQLException {
        return new MedicalService(rs.getObject("id", UUID.class), rs.getObject("category_id", UUID.class),
                rs.getString("name"), rs.getString("description"), rs.getBigDecimal("price"),
                rs.getInt("duration_minutes"), PerformerType.valueOf(rs.getString("performer_type")),
                rs.getBoolean("active"), rs.getLong("version"));
    }

    private static ServiceCategory category(ResultSet rs, int row) throws SQLException {
        return new ServiceCategory(rs.getObject("id", UUID.class), rs.getString("name"), rs.getLong("version"));
    }
}
