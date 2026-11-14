package pl.przychodnia360.common.security;

import java.util.Optional;

/**
 * Weryfikacja tokenu dostępu. Usługa wystawiająca tokeny (iam) weryfikuje własnymi kluczami, pozostałe usługi
 * kluczami pobranymi z JWKS wystawcy ({@link JwksKeyResolver}); żadna usługa poza iam nie ma sekretu ani klucza
 * prywatnego (ADR-M04). Pusty wynik oznacza token niepoprawny, wygasły lub odwołany.
 */
public interface AccessTokenVerifier {

    Optional<AuthenticatedUser> verify(String token);
}
