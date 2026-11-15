package pl.przychodnia360.tenant.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.tenant.api.dto.CreateTenantRequest;
import pl.przychodnia360.tenant.api.dto.TenantResponse;
import pl.przychodnia360.tenant.api.dto.UpdateTenantRequest;
import pl.przychodnia360.tenant.application.TenantService;

/** Zarządzanie placówkami przez administratora platformy (FR-43). Dostęp: PLATFORM_MANAGE. */
@RestController
@RequestMapping(value = "/api/v1/platform/tenants", produces = MediaType.APPLICATION_JSON_VALUE)
public class TenantAdminController {

    private final TenantService service;

    TenantAdminController(TenantService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@permissions.has(authentication, 'PLATFORM_MANAGE')")
    @Operation(operationId = "listTenants", summary = "Lista placówek", description = "Dostęp: PLATFORM_MANAGE (PLATFORM_ADMIN).")
    public List<TenantResponse> list() {
        return service.list().stream().map(TenantResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has(authentication, 'PLATFORM_MANAGE')")
    @Operation(operationId = "createTenant", summary = "Utworzenie placówki", description = "Dostęp: PLATFORM_MANAGE. 409 przy zajętym identyfikatorze.")
    public TenantResponse create(@Valid @RequestBody CreateTenantRequest request) {
        return TenantResponse.of(service.create(request.slug(), request.name()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@permissions.has(authentication, 'PLATFORM_MANAGE')")
    @Operation(operationId = "updateTenant", summary = "Edycja lub dezaktywacja placówki", description = "Dostęp: PLATFORM_MANAGE.")
    public TenantResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTenantRequest request) {
        return TenantResponse.of(service.update(id, request.name(), request.active()));
    }
}
