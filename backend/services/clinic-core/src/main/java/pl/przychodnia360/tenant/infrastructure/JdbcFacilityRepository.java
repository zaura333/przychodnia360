package pl.przychodnia360.tenant.infrastructure;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.tenant.application.FacilityRepository;
import pl.przychodnia360.tenant.domain.Facility;

@Repository
class JdbcFacilityRepository implements FacilityRepository {

    private final JdbcClient jdbc;

    JdbcFacilityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Tabela tenants nie podlega RLS (słownik placówek), więc zapytanie działa bez kontekstu placówki. */
    @Override
    public List<Facility> findActive() {
        return jdbc.sql("SELECT slug, name FROM tenants WHERE active ORDER BY name")
                .query((rs, i) -> new Facility(rs.getString("slug"), rs.getString("name")))
                .list();
    }
}
