package pl.przychodnia360.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.shared.ForbiddenException;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.shared.UnauthorizedException;
import pl.przychodnia360.tenant.api.TenantFacade;

/**
 * Wydawanie tokenów usługowych innym usługom (client credentials, ADR-M17): usługa przedstawia się nazwą i sekretem,
 * podaje placówkę i usługę docelową. Klucz prywatny zostaje w iam; usługa dostaje tylko krótko żyjący token, którego
 * {@code aud} musi być na jej liście, a placówka musi istnieć i być aktywna.
 */
@Component
@EnableConfigurationProperties(ServiceClientsProperties.class)
public class ServiceTokenIssuer {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceTokenIssuer.class);

    private final ServiceClientsProperties clients;
    private final JwtService jwt;
    private final TenantFacade tenants;
    private final TransactionTemplate tx;

    ServiceTokenIssuer(ServiceClientsProperties clients, JwtService jwt, TenantFacade tenants, TransactionTemplate tx) {
        this.tx = tx;
        this.clients = clients;
        this.jwt = jwt;
        this.tenants = tenants;
    }

    public record IssuedToken(String token, long expiresIn) {
    }

    public IssuedToken issue(String clientId, String clientSecret, UUID tenantId, String audience) {
        ServiceClientsProperties.Client client = clientId == null ? null : clients.serviceClients().get(clientId);
        // porównanie w stałym czasie także dla nieznanej usługi (brak wycieku przez czas odpowiedzi)
        String expected = client == null || client.secret() == null ? "" : client.secret();
        boolean secretOk = MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                (clientSecret == null ? "" : clientSecret).getBytes(StandardCharsets.UTF_8));
        if (client == null || expected.isBlank() || !secretOk) {
            LOG.warn("Odrzucono żądanie tokenu usługowego: nieznana usługa lub zły sekret (client={})", clientId);
            throw new UnauthorizedException("Niepoprawne dane usługi.");
        }
        if (audience == null || !client.audiences().contains(audience)) {
            throw new ForbiddenException("Usługa " + clientId + " nie może wołać usługi " + audience + ".");
        }
        boolean activeTenant;
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenantId))) {
            activeTenant = Boolean.TRUE.equals(tx.execute(s -> tenants.info(tenantId).map(TenantFacade.TenantInfo::active).orElse(false)));
        }
        if (!activeTenant) {
            throw new ForbiddenException("Placówka nie istnieje albo jest nieaktywna.");
        }
        return new IssuedToken(jwt.issueService(clientId, tenantId, audience),
                JwtService.SERVICE_TOKEN_TTL.toSeconds());
    }
}
