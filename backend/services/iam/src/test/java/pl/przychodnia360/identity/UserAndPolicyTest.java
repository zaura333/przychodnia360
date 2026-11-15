package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.common.security.Permission;
import pl.przychodnia360.common.security.PermissionPolicy;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.domain.LoginAttemptPolicy;
import pl.przychodnia360.identity.domain.User;

class UserAndPolicyTest {

    private final LoginAttemptPolicy policy = new LoginAttemptPolicy(3, Duration.ofMinutes(15));
    private final Instant now = Instant.parse("2026-11-14T10:00:00Z");

    private User user() {
        return User.create(UUID.randomUUID(), UUID.randomUUID(), "a@b.pl", Role.PATIENT, "hash");
    }

    @Test
    void locksAfterMaxFailedAttemptsAndUnlocksAfterDuration() {
        User u = user();
        u.registerFailedAttempt(policy, now);
        u.registerFailedAttempt(policy, now);
        assertThat(u.isLocked(now)).isFalse();
        u.registerFailedAttempt(policy, now);
        assertThat(u.isLocked(now)).isTrue();
        assertThat(u.isLocked(now.plus(Duration.ofMinutes(15)))).isFalse();
        assertThat(u.failedAttempts()).isZero();
    }

    @Test
    void successResetsCounterAndLock() {
        User u = user();
        u.registerFailedAttempt(policy, now);
        u.registerSuccess();
        assertThat(u.failedAttempts()).isZero();
    }

    @Test
    void blockedOrPasswordlessAccountCannotLogin() {
        User blocked = user();
        blocked.block();
        assertThat(blocked.canLogin()).isFalse();
        User pending = User.create(UUID.randomUUID(), UUID.randomUUID(), "c@d.pl", Role.DOCTOR, null);
        assertThat(pending.canLogin()).isFalse();
        pending.changePassword("new");
        assertThat(pending.canLogin()).isTrue();
    }

    @Test
    void everyRoleHasPermissionsAndPlatformAdminHasNoTenantPermissions() {
        for (Role role : Role.values()) {
            assertThat(PermissionPolicy.matrix().get(role)).as(role.name()).isNotEmpty();
        }
        for (Permission p : Permission.values()) {
            if (p != Permission.PLATFORM_MANAGE) {
                assertThat(PermissionPolicy.has(Role.PLATFORM_ADMIN, p)).as(p.name()).isFalse();
            }
        }
        assertThat(PermissionPolicy.has(Role.RECEPTIONIST, Permission.APPOINTMENT_CANCEL_ANY)).isTrue();
        assertThat(PermissionPolicy.has(Role.PATIENT, Permission.APPOINTMENT_CANCEL_ANY)).isFalse();
        assertThat(PermissionPolicy.has(Role.TENANT_ADMIN, Permission.APPOINTMENT_BOOK_ANY)).isFalse();
    }
}
