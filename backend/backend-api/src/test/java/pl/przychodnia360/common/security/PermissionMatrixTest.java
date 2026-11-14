package pl.przychodnia360.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pl.przychodnia360.AbstractIntegrationTest;

/**
 * Szkielet macierzy uprawnień: każdy endpoint aplikacji ma jawną deklarację dostępu
 * (@PreAuthorize albo @PublicEndpoint). Endpoint bez wpisu nie przechodzi CI.
 */
class PermissionMatrixTest extends AbstractIntegrationTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mapping;

    @Test
    void everyEndpointDeclaresAccess() {
        List<String> undeclared = new ArrayList<>();
        int checked = 0;
        for (var entry : mapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("pl.przychodnia360")) {
                continue; // endpointy bibliotek (springdoc, błędy) nie należą do macierzy aplikacji
            }
            checked++;
            Method method = handler.getMethod();
            boolean declared = AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
                    || AnnotatedElementUtils.hasAnnotation(method, PublicEndpoint.class)
                    || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), PublicEndpoint.class);
            if (!declared) {
                undeclared.add(entry.getKey() + " -> " + method);
            }
        }
        assertThat(checked).as("test nie może być pusty").isPositive();
        assertThat(undeclared).as("endpointy bez deklaracji uprawnień").isEmpty();
    }
}
