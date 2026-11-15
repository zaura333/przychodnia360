package pl.przychodnia360.identity.api.dto;

import java.util.UUID;
import pl.przychodnia360.common.security.Role;

/** Profil bieżącego użytkownika; identyfikatory pochodzą z tokenu, nazwa placówki z modułu tenant. */
public record MeResponse(UUID userId, Role role, UUID tenantId, String tenantName, UUID employeeId, UUID patientId,
        int slotStepMinutes) {
}
