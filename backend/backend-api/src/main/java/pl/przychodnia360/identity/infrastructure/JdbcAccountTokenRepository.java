package pl.przychodnia360.identity.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.identity.application.AccountTokenRepository;
import pl.przychodnia360.identity.domain.AccountToken;

@Repository
class JdbcAccountTokenRepository implements AccountTokenRepository {

    private final JdbcClient jdbc;

    JdbcAccountTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AccountToken t) {
        jdbc.sql("""
                INSERT INTO password_reset_tokens (id, user_id, tenant_id, purpose, token_hash, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .params(t.id(), t.userId(), t.tenantId(), t.purpose().name(), t.tokenHash(),
                        Timestamp.from(t.expiresAt()))
                .update();
    }

    @Override
    public Optional<AccountToken> findByHashForUpdate(String tokenHash) {
        return jdbc.sql("""
                SELECT id, user_id, tenant_id, purpose, token_hash, expires_at, used_at
                  FROM password_reset_tokens WHERE token_hash = ? FOR UPDATE
                """)
                .param(tokenHash)
                .query((rs, i) -> new AccountToken(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getObject("tenant_id", UUID.class), AccountToken.Purpose.valueOf(rs.getString("purpose")),
                        rs.getString("token_hash"), rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant()))
                .optional();
    }

    @Override
    public void markUsed(UUID id, Instant now) {
        jdbc.sql("UPDATE password_reset_tokens SET used_at = ? WHERE id = ?").params(Timestamp.from(now), id).update();
    }

    @Override
    public void invalidateAllForUser(UUID userId, Instant now) {
        jdbc.sql("UPDATE password_reset_tokens SET used_at = ? WHERE user_id = ? AND used_at IS NULL")
                .params(Timestamp.from(now), userId).update();
    }
}
