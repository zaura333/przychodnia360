package pl.przychodnia360.common.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class InternalApiConfig {

    /** Strażnik działa przed Spring Security, więc żądanie bez nagłówka usługowego nie dociera nawet do JWT. */
    @Bean
    FilterRegistrationBean<InternalApiGuardFilter> internalApiGuard(@Value("${p360.internal.token:}") String token) {
        FilterRegistrationBean<InternalApiGuardFilter> registration =
                new FilterRegistrationBean<>(new InternalApiGuardFilter(token));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.addUrlPatterns("/internal/*");
        return registration;
    }
}
