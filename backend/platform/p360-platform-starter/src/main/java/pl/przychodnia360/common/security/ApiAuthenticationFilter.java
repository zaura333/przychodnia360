package pl.przychodnia360.common.security;

import jakarta.servlet.Filter;

/**
 * Filtr uwierzytelniający API. Interfejs w common, implementacja (JWT) w module identity,
 * żeby common nie zależało od identity (brak cyklu modułów).
 */
public interface ApiAuthenticationFilter extends Filter {
}
