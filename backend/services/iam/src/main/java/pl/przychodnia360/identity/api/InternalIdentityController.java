package pl.przychodnia360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.identity.api.dto.EmployeeAccountRequest;
import pl.przychodnia360.identity.api.dto.EmployeeAccountResponse;

/**
 * Wewnętrzne API kont dla innych usług (S3): saga zakładania i dezaktywacji pracownika w {@code scheduling}. Dostępne
 * wyłącznie dla tokenu usługowego z {@code aud = iam} (rola SERVICE, jedna placówka), nie dla ról użytkowników, i nie
 * routowane przez krawędź. Placówka wynika z tokenu (RLS).
 */
@RestController
public class InternalIdentityController {

    private final IdentityFacade identity;

    InternalIdentityController(IdentityFacade identity) {
        this.identity = identity;
    }

    @PutMapping(value = "/api/v1/internal/employee-accounts/{employeeId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'IDENTITY_INTERNAL_WRITE')")
    @Operation(operationId = "internalEnsureEmployeeAccount", summary = "Idempotentne konto pracownika (wewnętrzne)",
            description = "Dostęp: IDENTITY_INTERNAL_WRITE (SERVICE). Powtórzenie zwraca to samo konto; e-mail innego konta: 409.")
    public EmployeeAccountResponse ensureEmployeeAccount(@PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeAccountRequest request) {
        return new EmployeeAccountResponse(identity.ensureEmployeeAccount(employeeId, request.email(), request.role()));
    }

    @PostMapping("/api/v1/internal/users/{userId}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissions.has(authentication, 'IDENTITY_INTERNAL_WRITE')")
    @Operation(operationId = "internalDeactivateUser", summary = "Dezaktywacja konta (wewnętrzne)",
            description = "Dostęp: IDENTITY_INTERNAL_WRITE (SERVICE). Idempotentne; unieważnia tokeny odświeżania.")
    public void deactivate(@PathVariable UUID userId) {
        identity.deactivate(userId);
    }
}
