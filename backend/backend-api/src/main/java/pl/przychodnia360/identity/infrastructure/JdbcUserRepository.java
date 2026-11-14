package pl.przychodnia360.identity.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.identity.api.Role;
import pl.przychodnia360.identity.application.UserRepository;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.identity.domain.UserStatus;
import pl.przychodnia360.shared.ConflictException;

@Repository
class JdbcUserRepository implements UserRepository {

    private static final String COLUMNS =
            "id, tenant_id, email, role, password_hash, status, failed_attempts, locked_until, employee_id, patient_id, version";

    private final JdbcClient jdbc;

    JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(User u) {
        try {
            jdbc.sql("""
                    INSERT INTO users (id, tenant_id, email, role, password_hash, status, employee_id, patient_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """)
                    .params(u.id(), u.tenantId(), u.email(), u.role().name(), u.passwordHash(), u.status().name(),
                            u.employeeId(), u.patientId())
                    .update();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("email-taken", "Konto z tym adresem e-mail już istnieje.");
        }
    }

    @Override
    public void update(User u) {
        int rows = jdbc.sql("""
                UPDATE users SET password_hash = ?, status = ?, failed_attempts = ?, locked_until = ?,
                       employee_id = ?, patient_id = ?, updated_at = now(), version = version + 1
                 WHERE id = ? AND version = ?
                """)
                .params(u.passwordHash(), u.status().name(), u.failedAttempts(),
                        u.lockedUntil() == null ? null : Timestamp.from(u.lockedUntil()), u.employeeId(), u.patientId(),
                        u.id(), u.version())
                .update();
        if (rows == 0) {
            throw new ConflictException("concurrent-update", "Konto zostało zmienione równolegle. Spróbuj ponownie.");
        }
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE id = ?").param(id)
                .query(JdbcUserRepository::map).optional();
    }

    @Override
    public Optional<User> findByEmail(UUID tenantId, String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE tenant_id = ? AND email = ?::citext")
                .params(tenantId, email).query(JdbcUserRepository::map).optional();
    }

    @Override
    public Optional<User> findPlatformAdminByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM users WHERE tenant_id IS NULL AND email = ?::citext")
                .param(email).query(JdbcUserRepository::map).optional();
    }

    @Override
    public void createTenantAdmin(UUID id, UUID tenantId, String email) {
        try {
            jdbc.sql("SELECT platform_create_tenant_admin(?, ?, ?, ?::text)").params(id, tenantId, email, null)
                    .query().singleValue();
        } catch (DuplicateKeyException e) {
            throw new ConflictException("email-taken", "Konto z tym adresem e-mail już istnieje.");
        }
    }

    private static User map(ResultSet rs, int row) throws SQLException {
        Timestamp locked = rs.getTimestamp("locked_until");
        return new User(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("email"),
                Role.valueOf(rs.getString("role")), rs.getString("password_hash"),
                UserStatus.valueOf(rs.getString("status")), rs.getInt("failed_attempts"),
                locked == null ? null : locked.toInstant(), rs.getObject("employee_id", UUID.class),
                rs.getObject("patient_id", UUID.class), rs.getLong("version"));
    }
}
