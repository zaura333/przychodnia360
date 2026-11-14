package pl.przychodnia360.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Jawne oznaczenie endpointu publicznego (bez uwierzytelnienia). Każdy endpoint musi mieć albo to,
 * albo {@code @PreAuthorize}; pilnuje tego test macierzy uprawnień. Ścieżka musi być też na liście
 * wyjątków w {@link SecurityConfig}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface PublicEndpoint {
}
