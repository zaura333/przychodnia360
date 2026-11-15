package pl.przychodnia360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.identity.api.dto.CreateTenantAdminRequest;
import pl.przychodnia360.identity.api.dto.TenantAdminResponse;
import pl.przychodnia360.identity.application.PlatformAccountService;

/** Konta administratorów placówek zakładane przez administratora platformy (FR-43). */
@RestController
public class PlatformAdminController {

    private final PlatformAccountService service;

    PlatformAdminController(PlatformAccountService service) {
        this.service = service;
    }

    @PostMapping(value = "/api/v1/platform/tenants/{id}/admins", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has(authentication, 'PLATFORM_MANAGE')")
    @Operation(operationId = "createTenantAdmin", summary = "Utworzenie konta administratora placówki",
            description = "Dostęp: PLATFORM_MANAGE. Konto bez hasła; administrator dostaje link ustawienia hasła w e-mailu.")
    public TenantAdminResponse createTenantAdmin(@PathVariable UUID id,
            @Valid @RequestBody CreateTenantAdminRequest request) {
        return new TenantAdminResponse(service.createTenantAdmin(id, request.email()), request.email().trim());
    }
}
