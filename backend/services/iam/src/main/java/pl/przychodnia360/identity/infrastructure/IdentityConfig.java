package pl.przychodnia360.identity.infrastructure;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.przychodnia360.common.security.BearerAuthenticationFilter;
import pl.przychodnia360.identity.domain.LoginAttemptPolicy;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
class IdentityConfig {

    @Bean
    BearerAuthenticationFilter bearerAuthenticationFilter(JwtService jwtService) {
        return new BearerAuthenticationFilter(jwtService);
    }

    /** Filtr działa wyłącznie w łańcuchu Spring Security, nie jako osobny filtr kontenera. */
    @Bean
    FilterRegistrationBean<BearerAuthenticationFilter> jwtFilterRegistration(BearerAuthenticationFilter filter) {
        FilterRegistrationBean<BearerAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** Blokada konta: domyślnie 5 prób i 15 minut (konfigurowalne). */
    @Bean
    LoginAttemptPolicy loginAttemptPolicy(@Value("${security.login.max-attempts:5}") int maxAttempts,
            @Value("${security.login.lock-minutes:15}") long lockMinutes) {
        return new LoginAttemptPolicy(maxAttempts, Duration.ofMinutes(lockMinutes));
    }
}
