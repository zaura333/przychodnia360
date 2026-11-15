package pl.przychodnia360.identity.infrastructure;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.api.IdentityFacade;
import pl.przychodnia360.shared.ConflictException;

/**
 * Jedyna implementacja {@link IdentityFacade} w scheduling (S3): polecenia REST do wewnętrznego API iam z tokenem
 * usługowym ({@code aud = iam}). Obie operacje są w iam idempotentne, więc saga pracownika może je powtarzać; awaria
 * iam kończy się 503 i wycofaniem lokalnej transakcji.
 */
@Component
class RemoteIdentityFacade implements IdentityFacade {

    private record AccountResponse(UUID userId) {
    }

    private final RemoteServiceClient client;

    RemoteIdentityFacade(@Qualifier("iamClient") RemoteServiceClient client) {
        this.client = client;
    }

    @Override
    public UUID ensureEmployeeAccount(UUID employeeId, String email, Role role) {
        try {
            AccountResponse response = client.put("/api/v1/internal/employee-accounts/{id}",
                    Map.of("email", email, "role", role.name()), AccountResponse.class, employeeId);
            return response.userId();
        } catch (ConflictException e) {
            throw new ConflictException("email-taken", "Konto z tym adresem e-mail już istnieje.");
        }
    }

    @Override
    public void deactivate(UUID userId) {
        client.post("/api/v1/internal/users/{id}/deactivate", Map.of(), Void.class, userId);
    }
}
