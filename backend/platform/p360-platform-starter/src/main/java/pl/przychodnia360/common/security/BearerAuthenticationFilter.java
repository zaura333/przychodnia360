package pl.przychodnia360.common.security;

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
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;

/**
 * Uwierzytelnia żądanie tokenem Bearer ({@link AccessTokenVerifier}) i na czas żądania ustawia kontekst placówki
 * z tokenu (tylko z tokenu, nigdy ze ścieżki ani z parametrów). Dla administratora platformy ustawia zakres
 * platformy. Wspólny dla wszystkich usług. Surowy token zostaje w poświadczeniach uwierzytelnienia, żeby wywołanie
 * innej usługi w ramach tego samego żądania mogło go przekazać dalej ({@code RemoteServiceClient}).
 */
public class BearerAuthenticationFilter extends OncePerRequestFilter implements ApiAuthenticationFilter {

    private static final String PREFIX = "Bearer ";

    private final AccessTokenVerifier verifier;

    public BearerAuthenticationFilter(AccessTokenVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith(PREFIX) ? header.substring(PREFIX.length()).trim() : null;
        Optional<AuthenticatedUser> user = token != null ? verifier.verify(token) : Optional.empty();
        if (user.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        AuthenticatedUser principal = user.get();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, token,
                List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
        try (TenantContext.Handle ignored = principal.role() == Role.PLATFORM_ADMIN
                ? TenantContext.openPlatformScope()
                : TenantContext.open(new TenantId(principal.tenantId()))) {
            chain.doFilter(request, response);
        }
    }
}
