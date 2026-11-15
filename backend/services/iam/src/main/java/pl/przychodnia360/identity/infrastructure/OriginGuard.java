package pl.przychodnia360.identity.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.przychodnia360.shared.ForbiddenException;

/**
 * Ochrona przed CSRF dla endpointów uwierzytelnianych ciasteczkiem (/auth/refresh, /auth/logout; D-029):
 * jeśli przeglądarka wysłała nagłówek Origin, musi być na liście dozwolonych (security.allowed-origins).
 * Dodatkowo ciasteczko ma SameSite=Strict. Klienci bez Origin (nie przeglądarki) nie są ograniczani.
 */
@Component
public class OriginGuard {

    private final List<String> allowed;

    OriginGuard(@Value("${security.allowed-origins:http://localhost:5173,http://localhost:8081}") List<String> allowed) {
        this.allowed = allowed;
    }

    public void verify(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowed.contains(origin)) {
            throw new ForbiddenException("Niedozwolone pochodzenie żądania.");
        }
    }
}
