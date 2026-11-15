package pl.przychodnia360.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pl.przychodnia360.AbstractIntegrationTest;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Permission;
import pl.przychodnia360.common.security.PermissionPolicy;
import pl.przychodnia360.common.security.Role;

/**
 * Macierz uprawnień (NFR-11): każdy endpoint aplikacji ma jawną deklarację dostępu, a dla każdej pary (rola, endpoint)
 * wynik egzekwowania przez Spring Security zgadza się z {@link PermissionPolicy}. Endpoint bez deklaracji nie przechodzi CI.
 *
 * <p>Egzekwowanie sprawdzamy wywołując metodę kontrolera przez proxy z ustawionym kontekstem bezpieczeństwa roli:
 * interceptor {@code @PreAuthorize} działa przed ciałem metody, więc odmowa to {@link AccessDeniedException}, a dla
 * dozwolonej roli metoda rusza (i kończy się dowolnym innym wyjątkiem, bo argumenty są puste).
 */
class PermissionMatrixTest extends AbstractIntegrationTest {

    /** Progi chroniące przed pustym testem (liczba endpointów usługi i par rola x endpoint). */
    private static final int MIN_ENDPOINTS = 20;
    private static final int MIN_CHECKS = 80;

    private static final Pattern PERMISSION = Pattern.compile("has\\(authentication, '(\\w+)'\\)");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private List<HandlerMethod> applicationEndpoints() {
        return mapping.getHandlerMethods().values().stream()
                .filter(h -> h.getBeanType().getPackageName().startsWith("pl.przychodnia360"))
                .toList();
    }

    private static boolean isPublic(HandlerMethod handler) {
        return AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PublicEndpoint.class)
                || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PublicEndpoint.class);
    }

    @Test
    void everyEndpointDeclaresAccess() {
        List<String> undeclared = new ArrayList<>();
        for (HandlerMethod handler : applicationEndpoints()) {
            boolean declared = AnnotatedElementUtils.hasAnnotation(handler.getMethod(), PreAuthorize.class) || isPublic(handler);
            if (!declared) {
                undeclared.add(handler.toString());
            }
        }
        assertThat(applicationEndpoints()).as("test nie może być pusty").hasSizeGreaterThan(MIN_ENDPOINTS);
        assertThat(undeclared).as("endpointy bez deklaracji uprawnień").isEmpty();
    }

    @Test
    void declaredPermissionsExistInThePolicyEnum() {
        Set<String> valid = java.util.Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        for (HandlerMethod handler : applicationEndpoints()) {
            for (String name : permissionsOf(handler)) {
                assertThat(valid).as("%s używa nieznanego uprawnienia", handler).contains(name);
            }
        }
    }

    @Test
    void publicEndpointsMatchTheSecurityConfigWhitelist() {
        Set<String> declared = new TreeSet<>();
        for (var entry : mapping.getHandlerMethods().entrySet()) {
            if (entry.getValue().getBeanType().getPackageName().startsWith("pl.przychodnia360") && isPublic(entry.getValue())) {
                declared.addAll(entry.getKey().getPathPatternsCondition().getPatternValues());
            }
        }
        // Lista w SecurityConfig jest wspólna dla usług, więc usługa wystawia jej podzbiór
        assertThat(declared).as("@PublicEndpoint tylko na ścieżkach z SecurityConfig.PUBLIC_PATHS")
                .isSubsetOf(SecurityConfig.PUBLIC_PATHS);
    }

    @Test
    void roleByEndpointEnforcementMatchesThePolicy() throws Exception {
        List<String> mismatches = new ArrayList<>();
        int checked = 0;
        for (HandlerMethod handler : applicationEndpoints()) {
            if (isPublic(handler)) {
                continue;
            }
            Set<String> permissions = permissionsOf(handler);
            for (Role role : Role.values()) {
                boolean expected = permissions.isEmpty()
                        || permissions.stream().anyMatch(p -> PermissionPolicy.has(role, Permission.valueOf(p)));
                boolean denied = invokeDenied(handler, role);
                checked++;
                if (denied == expected) {
                    mismatches.add(handler.getMethod().getDeclaringClass().getSimpleName() + "."
                            + handler.getMethod().getName() + " dla " + role + ": oczekiwano "
                            + (expected ? "dostępu" : "odmowy") + " (" + permissions + ")");
                }
            }
        }
        assertThat(checked).as("liczba sprawdzonych par rola x endpoint").isGreaterThan(MIN_CHECKS);
        assertThat(mismatches).isEmpty();
    }

    @Test
    void anonymousRequestsToProtectedEndpointsGet401() {
        List<String> failures = new ArrayList<>();
        for (var entry : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("pl.przychodnia360") || isPublic(handler)) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            String pattern = info.getPathPatternsCondition().getPatternValues().iterator().next();
            String path = pattern.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
            String method = info.getMethodsCondition().getMethods().iterator().next().name();
            var response = anonymous(method, path);
            if (response != 401) {
                failures.add(method + " " + path + " -> " + response);
            }
        }
        assertThat(failures).isEmpty();
    }

    private int anonymous(String method, String path) {
        try {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + path))
                    .method(method, java.net.http.HttpRequest.BodyPublishers.noBody())
                    // wewnętrzne API: z nagłówkiem usługowym, ale bez tokenu użytkownika (ma być 401, nie 404)
                    .header(pl.przychodnia360.common.internal.InternalApi.TOKEN_HEADER, "test-internal-token")
                    .build();
            return java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> permissionsOf(HandlerMethod handler) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
        Set<String> names = new TreeSet<>();
        if (annotation != null) {
            Matcher m = PERMISSION.matcher(annotation.value());
            while (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    /** Wywołuje metodę kontrolera przez proxy jako dana rola; true, gdy interceptor odmówił dostępu. */
    private boolean invokeDenied(HandlerMethod handler, Role role) throws IllegalAccessException {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(),
                role == Role.PLATFORM_ADMIN ? null : UUID.randomUUID(), role, null, null);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        Method method = handler.getMethod();
        Object bean = handler.createWithResolvedBean().getBean();
        try {
            method.invoke(bean, emptyArguments(method));
            return false;
        } catch (InvocationTargetException e) {
            return e.getCause() instanceof AccessDeniedException;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Object[] emptyArguments(Method method) {
        Object[] args = new Object[method.getParameterCount()];
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (types[i] == int.class) {
                args[i] = 0;
            } else if (types[i] == boolean.class) {
                args[i] = false;
            } else if (types[i] == long.class) {
                args[i] = 0L;
            }
        }
        return args;
    }
}
