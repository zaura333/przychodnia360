package pl.przychodnia360.common.internal;

import jakarta.servlet.http.HttpServletRequest;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.ForbiddenException;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.UnauthorizedException;
import pl.przychodnia360.shared.UnprocessableException;
import pl.przychodnia360.shared.ValidationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Klienci wewnętrznego API (ADR-M07): limit czasu, nagłówek usługowy i przekazanie tokenu dostępu bieżącego żądania.
 * Błędy drugiej usługi (Problem Details) są odtwarzane jako te same wyjątki domenowe, więc klient końcowy dostaje
 * taki sam kod i typ problemu, jak w monolicie. Niedostępność drugiej usługi kończy się błędem 500 (brak ponowień).
 */
public final class InternalClients {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private InternalClients() {
    }

    public static RestClient create(String baseUrl, String internalToken) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl + InternalApi.PREFIX)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(InternalApi.TOKEN_HEADER, internalToken);
                    String auth = currentAuthorization();
                    if (auth != null) {
                        request.getHeaders().set(HttpHeaders.AUTHORIZATION, auth);
                    }
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> rethrow(response))
                .build();
    }

    private static String currentAuthorization() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getHeader(HttpHeaders.AUTHORIZATION);
        }
        return null;
    }

    private static void rethrow(ClientHttpResponse response) throws java.io.IOException {
        int status = response.getStatusCode().value();
        String detail = "Błąd usługi wewnętrznej.";
        String type = null;
        try {
            JsonNode problem = JSON.readTree(response.getBody());
            if (problem.hasNonNull("detail")) {
                detail = problem.get("detail").asString();
            }
            if (problem.hasNonNull("type")) {
                type = problem.get("type").asString().replaceFirst("^/problems/", "");
            }
        } catch (RuntimeException ignored) {
            // brak treści Problem Details: zostaje komunikat ogólny
        }
        switch (status) {
            case 400 -> throw new ValidationException(detail);
            case 401 -> throw new UnauthorizedException(detail);
            case 403 -> throw new ForbiddenException(detail);
            case 404 -> throw new NotFoundException(detail);
            case 409 -> throw type == null ? new ConflictException(detail) : new ConflictException(type, detail);
            case 422 -> throw new UnprocessableException(type == null ? "unprocessable" : type, detail);
            default -> throw new IllegalStateException("Usługa wewnętrzna zwróciła " + status);
        }
    }
}
