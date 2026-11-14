package pl.przychodnia360.identity.domain;

import java.time.Instant;
import java.util.UUID;
import pl.przychodnia360.common.security.Role;

/** Konto użytkownika. tenantId jest null tylko dla PLATFORM_ADMIN; passwordHash null do czasu ustawienia hasła. */
public class User {

    private final UUID id;
    private final UUID tenantId;
    private final String email;
    private final Role role;
    private String passwordHash;
    private UserStatus status;
    private int failedAttempts;
    private Instant lockedUntil;
    private UUID employeeId;
    private UUID patientId;
    private final long version;

    public User(UUID id, UUID tenantId, String email, Role role, String passwordHash, UserStatus status,
            int failedAttempts, Instant lockedUntil, UUID employeeId, UUID patientId, long version) {
        this.id = id;
        this.tenantId = tenantId;
        this.email = email;
        this.role = role;
        this.passwordHash = passwordHash;
        this.status = status;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.employeeId = employeeId;
        this.patientId = patientId;
        this.version = version;
    }

    public static User create(UUID id, UUID tenantId, String email, Role role, String passwordHash) {
        return new User(id, tenantId, email, role, passwordHash, UserStatus.ACTIVE, 0, null, null, null, 0);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean canLogin() {
        return status == UserStatus.ACTIVE && passwordHash != null;
    }

    /** Zwiększa licznik; po przekroczeniu limitu blokuje czasowo i zeruje licznik. */
    public void registerFailedAttempt(LoginAttemptPolicy policy, Instant now) {
        failedAttempts++;
        if (failedAttempts >= policy.maxAttempts()) {
            lockedUntil = now.plus(policy.lockDuration());
            failedAttempts = 0;
        }
    }

    public void registerSuccess() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    public void linkEmployee(UUID employeeId) {
        this.employeeId = employeeId;
    }

    public void linkPatient(UUID patientId) {
        this.patientId = patientId;
    }

    public void block() {
        status = UserStatus.BLOCKED;
    }

    public void deactivate() {
        status = UserStatus.DEACTIVATED;
    }

    public UUID id() {
        return id;
    }

    public UUID tenantId() {
        return tenantId;
    }

    public String email() {
        return email;
    }

    public Role role() {
        return role;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserStatus status() {
        return status;
    }

    public int failedAttempts() {
        return failedAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public UUID employeeId() {
        return employeeId;
    }

    public UUID patientId() {
        return patientId;
    }

    public long version() {
        return version;
    }
}
