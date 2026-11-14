package pl.przychodnia360.patients.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.patients.application.PatientRepository;
import pl.przychodnia360.patients.domain.Patient;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.TenantContext;

@Repository
class JdbcPatientRepository implements PatientRepository {

    private static final String COLUMNS = "id, user_id, first_name, last_name, birth_date, contact_email, version";

    private final JdbcClient jdbc;

    JdbcPatientRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Patient p) {
        jdbc.sql("""
                INSERT INTO patients (id, tenant_id, user_id, first_name, last_name, birth_date, contact_email)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .params(p.id(), TenantContext.requireTenant().value(), p.userId(), p.firstName(), p.lastName(),
                        p.birthDate(), p.contactEmail())
                .update();
    }

    @Override
    public void update(Patient p) {
        int rows = jdbc.sql("""
                UPDATE patients SET user_id = ?, contact_email = ?, updated_at = now(), version = version + 1
                 WHERE id = ? AND version = ?
                """)
                .params(p.userId(), p.contactEmail(), p.id(), p.version()).update();
        if (rows == 0) {
            throw new ConflictException("concurrent-update", "Dane pacjenta zostały zmienione równolegle.");
        }
    }

    @Override
    public Optional<Patient> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM patients WHERE id = ?").param(id)
                .query(JdbcPatientRepository::map).optional();
    }

    /** Dla każdego fragmentu: (imię lub nazwisko) zaczyna się od fragmentu. Znaki LIKE są uciekane. */
    private static String where(List<String> tokens, List<Object> params) {
        StringBuilder sql = new StringBuilder(" WHERE true");
        for (String token : tokens) {
            String pattern = token.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            sql.append(" AND (lower(last_name) LIKE ? OR lower(first_name) LIKE ?)");
            params.add(pattern);
            params.add(pattern);
        }
        return sql.toString();
    }

    @Override
    public List<Patient> search(List<String> tokens, int offset, int limit) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT " + COLUMNS + " FROM patients" + where(tokens, params)
                + " ORDER BY lower(last_name), lower(first_name), id LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return jdbc.sql(sql).params(params).query(JdbcPatientRepository::map).list();
    }

    @Override
    public long countSearch(List<String> tokens) {
        List<Object> params = new ArrayList<>();
        return jdbc.sql("SELECT count(*) FROM patients" + where(tokens, params)).params(params)
                .query(Long.class).single();
    }

    private static Patient map(ResultSet rs, int row) throws SQLException {
        return new Patient(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("first_name"), rs.getString("last_name"), rs.getObject("birth_date", LocalDate.class),
                rs.getString("contact_email"), rs.getLong("version"));
    }
}
