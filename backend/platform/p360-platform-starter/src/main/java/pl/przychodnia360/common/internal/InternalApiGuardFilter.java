package pl.przychodnia360.common.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Wpuszcza na {@code /internal/**} tylko żądania z poprawnym nagłówkiem usługowym; pozostałe dostają 404, jakby
 * ścieżki nie było. Brak skonfigurowanego sekretu zamyka wewnętrzne API całkowicie (bezpieczne domyślne).
 */
public class InternalApiGuardFilter extends OncePerRequestFilter {

    private final byte[] expected;

    public InternalApiGuardFilter(String token) {
        this.expected = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(InternalApi.TOKEN_HEADER);
        if (expected == null || header == null
                || !MessageDigest.isEqual(expected, header.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
