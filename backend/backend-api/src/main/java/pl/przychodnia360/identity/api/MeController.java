package pl.przychodnia360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.tenant.api.TenantFacade;

@RestController
public class MeController {

    /** Profil bieżącego użytkownika; identyfikatory pochodzą z tokenu, nazwa placówki z modułu tenant. */
    public record MeResponse(UUID userId, Role role, UUID tenantId, String tenantName, UUID employeeId, UUID patientId,
            int slotStepMinutes) {
    }

    private final TenantFacade tenants;
    private final int slotStepMinutes;

    MeController(TenantFacade tenants, @Value("${appointments.slot-step-minutes:15}") int slotStepMinutes) {
        this.tenants = tenants;
        this.slotStepMinutes = slotStepMinutes;
    }

    @GetMapping(value = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getMe", summary = "Bieżący użytkownik", description = "Dostęp: każdy zalogowany użytkownik.")
    public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        String tenantName = user.tenantId() == null ? null : tenants.name(user.tenantId()).orElse(null);
        return new MeResponse(user.userId(), user.role(), user.tenantId(), tenantName, user.employeeId(),
                user.patientId(), slotStepMinutes);
    }
}
