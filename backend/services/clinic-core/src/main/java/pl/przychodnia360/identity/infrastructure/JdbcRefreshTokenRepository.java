package pl.przychodnia360.identity.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.przychodnia360.identity.application.RefreshTokenRepository;
import pl.przychodnia360.identity.domain.RefreshToken;

@Repository
class JdbcRefreshTokenRepository implements RefreshTokenRepository {

    private final JdbcClient jdbc;

    JdbcRefreshTokenRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(RefreshToken t) {
        jdbc.sql("""
                INSERT INTO refresh_tokens (id, family_id, user_id, tenant_id, token_hash, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .params(t.id(), t.familyId(), t.userId(), t.tenantId(), t.tokenHash(), Timestamp.from(t.expiresAt()))
                .update();
    }

    @Override
    public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
        return jdbc.sql("""
                SELECT id, family_id, user_id, tenant_id, token_hash, expires_at, used_at, revoked_at
                  FROM refresh_tokens WHERE token_hash = ? FOR UPDATE
                """)
                .param(tokenHash)
                .query((rs, i) -> new RefreshToken(rs.getObject("id", UUID.class), rs.getObject("family_id", UUID.class),
                        rs.getObject("user_id", UUID.class), rs.getObject("tenant_id", UUID.class),
                        rs.getString("token_hash"), rs.getTimestamp("expires_at").toInstant(),
                        instant(rs.getTimestamp("used_at")), instant(rs.getTimestamp("revoked_at"))))
                .optional();
    }

    @Override
    public void markUsed(UUID id, Instant now) {
        jdbc.sql("UPDATE refresh_tokens SET used_at = ? WHERE id = ?").params(Timestamp.from(now), id).update();
    }

    @Override
    public void revokeFamily(UUID familyId, Instant now) {
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = ? WHERE family_id = ? AND revoked_at IS NULL")
                .params(Timestamp.from(now), familyId).update();
    }

    @Override
    public void revokeAllForUser(UUID userId, Instant now) {
        jdbc.sql("UPDATE refresh_tokens SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL")
                .params(Timestamp.from(now), userId).update();
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
