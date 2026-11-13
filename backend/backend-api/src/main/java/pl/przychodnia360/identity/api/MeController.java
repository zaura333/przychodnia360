package pl.przychodnia360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    /** Zalogowany użytkownik. Pola docelowe (placówka, rola) wypełnia uwierzytelnianie JWT z etapu 1a. */
    public record MeResponse(String userId, String role, UUID tenantId) {
    }

    @GetMapping(value = "/api/v1/me", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getMe", summary = "Bieżący użytkownik", description = "Dostęp: każdy zalogowany użytkownik.")
    public MeResponse me(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .findFirst()
                .orElse("NONE");
        return new MeResponse(authentication.getName(), role, null);
    }
}
