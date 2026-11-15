package pl.przychodnia360.common.security.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Weryfikacja tokenów dostępu w każdej usłudze. Na razie wspólny klucz HS256 we wszystkich usługach (świadomy dług,
 * ADR-M07); docelowo podpis asymetryczny i JWKS z iam-service (ADR-M04, zadanie 1.2).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    @Bean
    JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    /** Filtr działa wyłącznie w łańcuchu Spring Security, nie jako osobny filtr kontenera. */
    @Bean
    FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
