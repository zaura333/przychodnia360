package pl.przychodnia360.common.http;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.ForbiddenException;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ServiceUnavailableException;
import pl.przychodnia360.shared.UnprocessableException;
import pl.przychodnia360.shared.ValidationException;

/**
 * Klient REST do innej usługi platformy (S1, S2): limity czasu połączenia i odczytu, wyłącznik obwodu i tłumaczenie
 * odpowiedzi na wyjątki domenowe. Token bieżącego żądania jest przekazywany dalej (kontekst placówki wynika z tokenu,
 * więc usługa docelowa stosuje własne RLS); dopóki nie ma tokenu usługowego, wywołanie poza żądaniem HTTP kończy się
 * błędem. Błędy 4xx są odpowiedzią biznesową (nie otwierają obwodu), a limit czasu, błąd połączenia i 5xx są awarią.
 */
public class RemoteServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RemoteServiceClient.class);

    private final String name;
    private final RestClient client;
    private final CircuitBreaker breaker;
    private final Supplier<String> serviceToken;
    private final boolean asCaller;

    /** Klient bez tokenu usługowego: przekazuje token wywołującego (seed, testy). */
    public RemoteServiceClient(String name, String baseUrl, Duration connectTimeout, Duration readTimeout,
            CircuitBreaker breaker, RestClient.Builder builder) {
        this(name, baseUrl, connectTimeout, readTimeout, breaker, builder, null);
    }

    /**
     * {@code serviceToken} zwraca token usługowy dla bieżącej placówki i usługi docelowej (albo null, gdy nie ma
     * kontekstu placówki). Gdy jest ustawiony, wywołania używają tożsamości usługi, a nie tokenu użytkownika, więc
     * działają także poza żądaniem HTTP (rejestracja, zadania w tle) i nie przenoszą uprawnień użytkownika dalej.
     */
    public RemoteServiceClient(String name, String baseUrl, Duration connectTimeout, Duration readTimeout,
            CircuitBreaker breaker, RestClient.Builder builder, Supplier<String> serviceToken) {
        this.name = name;
        this.breaker = breaker;
        this.serviceToken = serviceToken;
        this.asCaller = false;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        factory.setReadTimeout(readTimeout);
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    private RemoteServiceClient(RemoteServiceClient origin, boolean asCaller) {
        this.name = origin.name;
        this.client = origin.client;
        this.breaker = origin.breaker;
        this.serviceToken = origin.serviceToken;
        this.asCaller = asCaller;
    }

    /** Widok tego samego klienta (ten sam obwód), który przekazuje token wywołującego zamiast tokenu usługowego. */
    public RemoteServiceClient asCaller() {
        return new RemoteServiceClient(this, true);
    }

    public <T> T get(String uri, ParameterizedTypeReference<T> type, Object... vars) {
        return call(() -> client.get().uri(uri, vars).headers(h -> authorize(h)).retrieve().body(type));
    }

    public <T> T post(String uri, Object body, Class<T> type, Object... vars) {
        return call(() -> client.post().uri(uri, vars).headers(h -> authorize(h)).body(body).retrieve().body(type));
    }

    /** POST bez treści (polecenia bez parametrów, np. dezaktywacja): nie wysyła pustego obiektu, którego kontrakt nie przewiduje. */
    public void postWithoutBody(String uri, Object... vars) {
        call(() -> client.post().uri(uri, vars).headers(h -> authorize(h)).retrieve().toBodilessEntity());
    }

    public <T> T put(String uri, Object body, Class<T> type, Object... vars) {
        return call(() -> client.put().uri(uri, vars).headers(h -> authorize(h)).body(body).retrieve().body(type));
    }

    public void put(String uri, Object body, Object... vars) {
        call(() -> client.put().uri(uri, vars).headers(h -> authorize(h)).body(body).retrieve().toBodilessEntity());
    }

    private void authorize(org.springframework.http.HttpHeaders headers) {
        if (!asCaller && serviceToken != null) {
            String token = serviceToken.get();
            if (token != null && !token.isBlank()) {
                headers.setBearerAuth(token);
                return;
            }
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String token && !token.isBlank()) {
            headers.setBearerAuth(token);
        }
    }

    private <T> T call(java.util.function.Supplier<T> request) {
        if (!breaker.tryAcquire()) {
            throw new ServiceUnavailableException("Usługa " + name + " jest chwilowo niedostępna. Spróbuj ponownie za chwilę.");
        }
        try {
            T result = request.get();
            breaker.onSuccess();
            return result;
        } catch (RestClientResponseException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.is5xxServerError()) {
                breaker.onFailure();
                log.warn("Usługa {} odpowiedziała {}", name, status.value());
                throw new ServiceUnavailableException("Usługa " + name + " zgłosiła błąd. Spróbuj ponownie za chwilę.");
            }
            breaker.onSuccess();
            throw translate(status.value());
        } catch (RuntimeException e) {
            breaker.onFailure();
            log.warn("Usługa {} niedostępna: {}", name, e.toString());
            throw new ServiceUnavailableException("Usługa " + name + " nie odpowiada. Spróbuj ponownie za chwilę.");
        }
    }

    private RuntimeException translate(int status) {
        return switch (status) {
            case 400 -> new ValidationException("Usługa " + name + " odrzuciła dane.");
            case 404 -> new NotFoundException("Nie znaleziono zasobu.");
            case 403 -> new ForbiddenException("Brak uprawnień.");
            case 409 -> new ConflictException("Konflikt danych w usłudze " + name + ".");
            case 422 -> new UnprocessableException("rejected", "Usługa " + name + " odrzuciła operację.");
            default -> new ServiceUnavailableException("Usługa " + name + " zwróciła nieoczekiwaną odpowiedź (" + status + ").");
        };
    }
}
