package pl.przychodnia360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.security.PublicEndpoint;
import pl.przychodnia360.identity.api.dto.ServiceTokenRequest;
import pl.przychodnia360.identity.api.dto.ServiceTokenResponse;
import pl.przychodnia360.identity.infrastructure.ServiceTokenIssuer;
import pl.przychodnia360.identity.infrastructure.ServiceTokenIssuer.IssuedToken;

/**
 * Tokeny usługowe dla innych usług (client credentials, ADR-M17). Bez tokenu użytkownika: usługa uwierzytelnia się
 * nazwą i sekretem w treści żądania. Ścieżka wewnętrzna, nie routowana przez krawędź.
 */
@RestController
public class ServiceTokenController {

    private final ServiceTokenIssuer issuer;

    ServiceTokenController(ServiceTokenIssuer issuer) {
        this.issuer = issuer;
    }

    @PostMapping(value = "/api/v1/internal/service-tokens", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @PublicEndpoint
    @Operation(operationId = "internalIssueServiceToken", summary = "Token usługowy (wewnętrzne)",
            description = "Dostęp: sekret usługi. Zły sekret: 401; usługa docelowa spoza listy lub nieaktywna placówka: 403.")
    public ServiceTokenResponse issue(@Valid @RequestBody ServiceTokenRequest request) {
        IssuedToken token = issuer.issue(request.clientId(), request.clientSecret(), request.tenantId(), request.audience());
        return new ServiceTokenResponse(token.token(), token.expiresIn());
    }
}
