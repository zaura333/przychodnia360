package pl.przychodnia360.identity.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.internal.InternalApi;
import pl.przychodnia360.common.security.Role;

/** Wewnętrzne API kont pracowników dla scheduling-service (ADR-M07); wywołuje je administrator placówki. */
@Hidden
@RestController
public class InternalIdentityController {

    public record AccountRequest(@NotBlank @Email String email, @NotNull Role role) {
    }

    public record AccountResponse(UUID userId) {
    }

    public record LinkRequest(@NotNull UUID employeeId) {
    }

    private final IdentityFacade identity;

    InternalIdentityController(IdentityFacade identity) {
        this.identity = identity;
    }

    @PreAuthorize("@permissions.has(authentication, 'EMPLOYEE_MANAGE')")
    @PostMapping(value = InternalApi.PREFIX + "/employee-accounts", produces = MediaType.APPLICATION_JSON_VALUE)
    public AccountResponse createEmployeeAccount(@Valid @RequestBody AccountRequest request) {
        return new AccountResponse(identity.createEmployeeAccount(request.email(), request.role()));
    }

    @PreAuthorize("@permissions.has(authentication, 'EMPLOYEE_MANAGE')")
    @PostMapping(InternalApi.PREFIX + "/users/{id}/employee-link")
    public ResponseEntity<Void> linkEmployee(@PathVariable UUID id, @Valid @RequestBody LinkRequest request) {
        identity.linkEmployee(id, request.employeeId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@permissions.has(authentication, 'EMPLOYEE_MANAGE')")
    @PostMapping(InternalApi.PREFIX + "/users/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        identity.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
