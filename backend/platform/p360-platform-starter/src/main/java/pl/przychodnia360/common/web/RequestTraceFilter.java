package pl.przychodnia360.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.przychodnia360.shared.TenantContext;

/**
 * Identyfikator żądania (nagłówek {@code X-Request-Id} albo nowy UUID) do MDC i odpowiedzi.
 * Po żądaniu czyści MDC i {@link TenantContext}, żeby nic nie przeciekło do kolejnego żądania na tym wątku.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    /** Tylko bezpieczne znaki, żeby nagłówek nie służył do wstrzykiwania treści do logów. */
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && VALID_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.clear();
        }
    }

    /** Bieżący identyfikator żądania (dla Problem Details), albo {@code null} poza żądaniem. */
    public static String currentRequestId() {
        return MDC.get(MDC_REQUEST_ID);
    }
}
