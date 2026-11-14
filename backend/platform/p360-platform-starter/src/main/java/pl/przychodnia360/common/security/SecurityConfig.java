package pl.przychodnia360.common.security;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import pl.przychodnia360.common.web.RequestTraceFilter;

/**
 * Szkielet bezstanowego łańcucha filtrów z domyślnym odmawianiem. Uwierzytelnianie JWT dochodzi w etapie 1a.
 * Wyjątki od odmowy to wyłącznie jawna lista publicznych ścieżek poniżej.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Publiczne ścieżki (każda odpowiada endpointowi oznaczonemu {@link PublicEndpoint}). */
    static final String[] PUBLIC_PATHS = {
        "/api/v1/health",
        "/api/v1/health/db",
        "/api/v1/facilities",
        "/api/v1/auth/login",
        "/api/v1/auth/platform/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/register-patient",
        "/api/v1/auth/password-reset/request",
        "/api/v1/auth/password-reset/confirm",
    };

    /** Dokumentacja API: w profilu demo wyłączona właściwościami springdoc (wtedy 404). */
    static final String[] DOCS_PATHS = {
        "/api/v1/docs",
        "/api/v1/docs/**",
        "/swagger-ui/**",
    };

    @Bean
    SecurityFilterChain apiFilterChain(HttpSecurity http, ObjectProvider<ApiAuthenticationFilter> authFilter)
            throws Exception {
        authFilter.ifAvailable(f -> http.addFilterBefore(f, UsernamePasswordAuthenticationFilter.class));
        http
            .csrf(AbstractHttpConfigurer::disable) // API Bearer, bez ciasteczek sesyjnych
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(h -> h
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                .contentTypeOptions(c -> { }))
            .authorizeHttpRequests(a -> a
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers(DOCS_PATHS).permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(unauthorized())
                .accessDeniedHandler(forbidden()));
        return http.build();
    }

    /** Do czasu JWT (1a) brak źródła użytkowników; pusty menedżer zapobiega wygenerowaniu domyślnego hasła. */
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    private static AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> writeProblem(response, HttpStatus.UNAUTHORIZED, "unauthorized",
                "Brak uwierzytelnienia", "Wymagane jest zalogowanie.");
    }

    private static AccessDeniedHandler forbidden() {
        return (request, response, ex) -> writeProblem(response, HttpStatus.FORBIDDEN, "forbidden",
                "Brak uprawnień", "Nie masz uprawnień do tej operacji.");
    }

    /** Wszystkie wartości są stałymi, a traceId ma zwalidowany format, więc ręczne składanie JSON jest bezpieczne. */
    private static void writeProblem(HttpServletResponse response, HttpStatus status, String type, String title,
            String detail) throws java.io.IOException {
        String traceId = RequestTraceFilter.currentRequestId();
        String json = "{\"type\":\"/problems/" + type + "\",\"title\":\"" + title + "\",\"status\":" + status.value()
                + ",\"detail\":\"" + detail + "\""
                + (traceId == null ? "" : ",\"traceId\":\"" + traceId + "\"") + "}";
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
    }
}
