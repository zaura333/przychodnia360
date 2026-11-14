package pl.przychodnia360.common.security;

import java.util.UUID;

/** Użytkownik żądania, budowany z claimów tokenu dostępu. {@code tenantId} jest null tylko dla PLATFORM_ADMIN. */
public record AuthenticatedUser(UUID userId, UUID tenantId, Role role, UUID employeeId, UUID patientId) {

    public boolean is(Role expected) {
        return role == expected;
    }
}
