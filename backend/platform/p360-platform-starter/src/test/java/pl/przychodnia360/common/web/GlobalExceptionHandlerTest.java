package pl.przychodnia360.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ValidationException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUp() {
        MDC.put(RequestTraceFilter.MDC_REQUEST_ID, "trace-1234-abcd");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void notFoundMapsTo404WithTraceId() {
        var response = handler.notFound(new NotFoundException("Brak placówki"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        var body = response.getBody();
        assertThat(body.getType().toString()).isEqualTo("/problems/not-found");
        assertThat(body.getDetail()).isEqualTo("Brak placówki");
        assertThat(body.getProperties()).containsEntry("traceId", "trace-1234-abcd");
    }

    @Test
    void conflictKeepsCustomProblemType() {
        var response = handler.conflict(new ConflictException("slot-taken", "Termin zajęty"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().getType().toString()).endsWith("/slot-taken");
    }

    @Test
    void validationCarriesFieldErrors() {
        var ex = new ValidationException("Błędy", java.util.List.of(new ValidationException.FieldError("email", "zły")));
        var body = handler.validation(ex).getBody();

        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getProperties()).containsKey("errors");
    }

    @Test
    void unexpectedErrorIsGenericAndDoesNotLeak() {
        var response = handler.unexpected(new IllegalStateException(
                "ERROR: relation \"patients\" does not exist; pesel=44051401359"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        var body = response.getBody();
        assertThat(body.getDetail()).doesNotContain("relation").doesNotContain("pesel").doesNotContain("44051401359");
        assertThat(body.getTitle()).isEqualTo("Błąd serwera");
        assertThat(body.getProperties()).containsEntry("traceId", "trace-1234-abcd");
    }

    @Test
    void securityExceptionsArePassedThrough() {
        assertThatThrownBy(() -> handler.passThroughSecurity(new AccessDeniedException("x")))
                .isInstanceOf(AccessDeniedException.class);
    }
}
