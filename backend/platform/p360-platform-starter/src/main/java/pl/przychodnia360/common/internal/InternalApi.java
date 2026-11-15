package pl.przychodnia360.common.internal;

/**
 * Wewnętrzne API między usługami (ADR-M07). Ścieżki {@code /internal/**} nie są wystawiane przez krawędź (Traefik
 * kieruje tylko {@code /api/v1/**}), a każde wywołanie musi nieść współdzielony nagłówek {@link #TOKEN_HEADER}.
 * Wywołujący przekazuje token dostępu użytkownika, więc kontekst placówki i uprawnienia pochodzą z tego tokenu.
 */
public final class InternalApi {

    public static final String PREFIX = "/internal/v1";
    public static final String TOKEN_HEADER = "X-P360-Internal-Token";

    private InternalApi() {
    }
}
