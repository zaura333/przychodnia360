package pl.przychodnia360.identity.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.przychodnia360.common.security.ApiAuthenticationFilter;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;

/**
 * Uwierzytelnia żądanie tokenem Bearer i na czas żądania ustawia kontekst placówki z tokenu (tylko z tokenu,
 * nigdy ze ścieżki ani z parametrów). Dla administratora platformy ustawia zakres platformy.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter implements ApiAuthenticationFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        Optional<AuthenticatedUser> user = header != null && header.startsWith(PREFIX)
                ? jwtService.verify(header.substring(PREFIX.length()).trim())
                : Optional.empty();
        if (user.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        AuthenticatedUser principal = user.get();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
        try (TenantContext.Handle ignored = principal.role() == Role.PLATFORM_ADMIN
                ? TenantContext.openPlatformScope()
                : TenantContext.open(new TenantId(principal.tenantId()))) {
            chain.doFilter(request, response);
        }
    }
}
