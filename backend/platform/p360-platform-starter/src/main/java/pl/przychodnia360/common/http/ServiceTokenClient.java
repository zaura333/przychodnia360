package pl.przychodnia360.common.http;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pl.przychodnia360.shared.ServiceUnavailableException;

/**
 * Tokeny usługowe pobierane od iam (client credentials, ADR-M17) dla usług, które nie mają klucza podpisu: usługa
 * przedstawia się nazwą i sekretem, a dostaje krótko żyjący token dla jednej placówki i jednej usługi docelowej.
 * Tokeny są trzymane w pamięci do chwili tuż przed wygaśnięciem, więc iam jest wołany rzadko.
 */
public class ServiceTokenClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenClient.class);
    /** Zapas przed wygaśnięciem: token nie wygasa w trakcie wywołania usługi docelowej. */
    private static final Duration MARGIN = Duration.ofSeconds(15);

    private record Key(UUID tenantId, String audience) {
    }

    private record Cached(String token, Instant refreshAt) {
    }

    private record TokenRequest(String clientId, String clientSecret, UUID tenantId, String audience) {
    }

    private record TokenResponse(String accessToken, long expiresIn) {
    }

    private final RestClient client;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;
    private final Map<Key, Cached> cache = new ConcurrentHashMap<>();

    public ServiceTokenClient(String iamUrl, String clientId, String clientSecret, Duration timeout,
            RestClient.Builder builder, Clock clock) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.client = builder.baseUrl(iamUrl).requestFactory(factory).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    /** Token usługowy tej usługi dla placówki i usługi docelowej; awaria iam to {@link ServiceUnavailableException}. */
    public String token(UUID tenantId, String audience) {
        Key key = new Key(tenantId, audience);
        Instant now = clock.instant();
        Cached cached = cache.get(key);
        if (cached != null && now.isBefore(cached.refreshAt())) {
            return cached.token();
        }
        TokenResponse response;
        try {
            response = client.post().uri("/api/v1/internal/service-tokens")
                    .body(new TokenRequest(clientId, clientSecret, tenantId, audience))
                    .retrieve().body(TokenResponse.class);
        } catch (RuntimeException e) {
            log.warn("Nie udało się pobrać tokenu usługowego od iam (aud={}): {}", audience, e.toString());
            throw new ServiceUnavailableException("Usługa iam nie wydała tokenu usługowego. Spróbuj ponownie za chwilę.");
        }
        if (response == null || response.accessToken() == null) {
            throw new ServiceUnavailableException("Usługa iam nie wydała tokenu usługowego. Spróbuj ponownie za chwilę.");
        }
        Duration ttl = Duration.ofSeconds(response.expiresIn()).minus(MARGIN);
        cache.put(key, new Cached(response.accessToken(), now.plus(ttl.isNegative() ? Duration.ZERO : ttl)));
        return response.accessToken();
    }
}
