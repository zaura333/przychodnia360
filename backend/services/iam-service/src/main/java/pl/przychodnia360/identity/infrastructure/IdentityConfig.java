package pl.przychodnia360.identity.infrastructure;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.przychodnia360.identity.domain.LoginAttemptPolicy;

@Configuration
class IdentityConfig {

    /** Blokada konta: domyślnie 5 prób i 15 minut (konfigurowalne). */
    @Bean
    LoginAttemptPolicy loginAttemptPolicy(@Value("${security.login.max-attempts:5}") int maxAttempts,
            @Value("${security.login.lock-minutes:15}") long lockMinutes) {
        return new LoginAttemptPolicy(maxAttempts, Duration.ofMinutes(lockMinutes));
    }
}
