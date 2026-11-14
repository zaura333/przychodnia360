package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.AbstractIntegrationTest;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.application.PasswordHasher;
import pl.przychodnia360.identity.application.UserRepository;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;

/** RLS na tabelach tożsamości: izolacja placówek, zakres platformy, funkcja tworząca administratora placówki. */
class IdentityRlsTest extends AbstractIntegrationTest {

    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcClient jdbc;

    private UUID newTenant(String slug) {
        UUID id = UUID.randomUUID();
        try (var h = TenantContext.openWithoutTenant()) {
            tx.executeWithoutResult(s -> jdbc.sql("INSERT INTO tenants (id, slug, name) VALUES (?, ?, ?)")
                    .params(id, slug, slug).update());
        }
        return id;
    }

    private void inTenant(UUID tenant, Runnable r) {
        try (var h = TenantContext.open(new TenantId(tenant))) {
            tx.executeWithoutResult(s -> r.run());
        }
    }

    @Test
    void userIsVisibleOnlyInOwnTenantAndEmailIsUniquePerTenant() {
        UUID a = newTenant("rls-a");
        UUID b = newTenant("rls-b");
        UUID userId = Ids.newId();
        inTenant(a, () -> users.insert(User.create(userId, a, "same@x.pl", Role.PATIENT, hasher.hash("pw"))));
        inTenant(b, () -> users.insert(User.create(Ids.newId(), b, "same@x.pl", Role.PATIENT, hasher.hash("pw"))));

        inTenant(a, () -> assertThat(users.findById(userId)).isPresent());
        inTenant(b, () -> assertThat(users.findById(userId)).isEmpty());
        inTenant(a, () -> assertThat(users.findByEmail(a, "SAME@x.pl")).isPresent());
        assertThatThrownBy(() -> inTenant(a,
                () -> users.insert(User.create(Ids.newId(), a, "same@x.pl", Role.DOCTOR, "h"))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotInsertUserForAnotherTenant() {
        UUID a = newTenant("rls-c");
        UUID b = newTenant("rls-d");
        assertThatThrownBy(() -> inTenant(a,
                () -> users.insert(User.create(Ids.newId(), b, "x@x.pl", Role.PATIENT, "h"))))
                .hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }

    @Test
    void platformScopeSeesOnlyPlatformUsers() {
        UUID a = newTenant("rls-e");
        UUID tenantUser = Ids.newId();
        inTenant(a, () -> users.insert(User.create(tenantUser, a, "t@x.pl", Role.PATIENT, "h")));
        try (var h = TenantContext.openPlatformScope()) {
            tx.executeWithoutResult(s -> users.insert(
                    User.create(Ids.newId(), null, "root@platform.pl", Role.PLATFORM_ADMIN, hasher.hash("pw"))));
            tx.executeWithoutResult(s -> {
                assertThat(users.findPlatformAdminByEmail("root@platform.pl")).isPresent();
                assertThat(users.findById(tenantUser)).as("platforma nie widzi użytkowników placówki").isEmpty();
            });
        }
        inTenant(a, () -> assertThat(users.findPlatformAdminByEmail("root@platform.pl")).isEmpty());
    }

    @Test
    void platformCreatesTenantAdminThroughSecurityDefinerFunction() {
        UUID a = newTenant("rls-f");
        UUID adminId = Ids.newId();
        try (var h = TenantContext.openPlatformScope()) {
            tx.executeWithoutResult(s -> jdbc.sql("SELECT platform_create_tenant_admin(?, ?, ?, ?)")
                    .params(adminId, a, "admin@a.pl", "hash").query().singleValue());
        }
        inTenant(a, () -> {
            User u = users.findById(adminId).orElseThrow();
            assertThat(u.role()).isEqualTo(Role.TENANT_ADMIN);
        });
    }
}
