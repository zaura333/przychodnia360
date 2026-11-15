package pl.przychodnia360.common.security;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Uwierzytelnianie usług innych niż iam: tokeny weryfikowane kluczami publicznymi z JWKS wystawcy
 * ({@code security.jwt.jwks-url}); usługa nie ma żadnego sekretu. W iam ustawienie jest puste, a weryfikator
 * i filtr tworzy moduł identity (własne klucze).
 */
@Configuration
@ConditionalOnExpression("!'${security.jwt.jwks-url:}'.isEmpty()")
class JwksSecurityConfig {

    @Bean
    AccessTokenVerifier jwksAccessTokenVerifier(@Value("${security.jwt.jwks-url}") String jwksUrl,
            @Value("${security.jwt.issuer:przychodnia360}") String issuer,
            @Value("${security.jwt.jwks-timeout-millis:2000}") int timeoutMillis, Clock clock,
            AccessRevocations revocations, @Value("${spring.application.name}") String serviceName) {
        // nazwa usługi to odbiorca tokenów usługowych (aud)
        return new RsaAccessTokenVerifier(new JwksKeyResolver(jwksUrl, timeoutMillis), issuer, clock, revocations,
                serviceName);
    }

    @Bean
    BearerAuthenticationFilter bearerAuthenticationFilter(AccessTokenVerifier verifier) {
        return new BearerAuthenticationFilter(verifier);
    }

    /** Filtr działa wyłącznie w łańcuchu Spring Security, nie jako osobny filtr kontenera. */
    @Bean
    FilterRegistrationBean<BearerAuthenticationFilter> bearerFilterRegistration(BearerAuthenticationFilter filter) {
        FilterRegistrationBean<BearerAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
