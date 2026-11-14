package pl.przychodnia360.common.web;

import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.DomainException;
import pl.przychodnia360.shared.ForbiddenException;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ValidationException;

/**
 * Mapuje wyjątki na Problem Details (RFC 9457) z polami type, title, status i traceId.
 * Zasada: bez stack trace i bez komunikatów SQL w odpowiedzi; nieznany błąd to 500 z ogólnym tekstem.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static ProblemDetail problem(HttpStatusCode status, String type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("/problems/" + type));
        pd.setTitle(title);
        String traceId = RequestTraceFilter.currentRequestId();
        if (traceId != null) {
            pd.setProperty("traceId", traceId);
        }
        return pd;
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(NotFoundException ex) {
        return of(HttpStatus.NOT_FOUND, ex, "Nie znaleziono zasobu");
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ProblemDetail> conflict(ConflictException ex) {
        return of(HttpStatus.CONFLICT, ex, "Konflikt");
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ProblemDetail> forbidden(ForbiddenException ex) {
        return of(HttpStatus.FORBIDDEN, ex, "Brak uprawnień");
    }

    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ProblemDetail> validation(ValidationException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, ex.problemType(), "Niepoprawne dane", ex.getMessage());
        pd.setProperty("errors", ex.errors());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    /** Wyjątki bezpieczeństwa obsługuje łańcuch filtrów (401/403), więc przepuszczamy je dalej. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void passThroughSecurity(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        // Szczegóły tylko do logu (z identyfikatorem żądania w MDC), nigdy do odpowiedzi.
        log.error("Nieobsłużony błąd", ex);
        ProblemDetail pd = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Błąd serwera",
                "Wystąpił nieoczekiwany błąd. Podaj identyfikator żądania, jeśli zgłaszasz problem.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(pd);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ValidationException.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ValidationException.FieldError(e.getField(),
                        e.getDefaultMessage() == null ? "Niepoprawna wartość" : e.getDefaultMessage()))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation", "Niepoprawne dane",
                "Żądanie zawiera niepoprawne dane.");
        pd.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(pd);
    }

    /** Pozostałe wyjątki MVC (404, 405, 415, brak parametru): ten sam kształt, bez wewnętrznych szczegółów. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String title = status == null ? "Błąd" : status.getReasonPhrase();
        ProblemDetail pd = problem(statusCode, slug(status), title, defaultDetail(status));
        return ResponseEntity.status(statusCode).headers(headers).body(pd);
    }

    private static ResponseEntity<ProblemDetail> of(HttpStatus status, DomainException ex, String title) {
        return ResponseEntity.status(status).body(problem(status, ex.problemType(), title, ex.getMessage()));
    }

    private static String slug(HttpStatus status) {
        return status == null ? "error" : status.name().toLowerCase().replace('_', '-');
    }

    private static String defaultDetail(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "Nie znaleziono zasobu.";
        }
        if (status != null && status.is4xxClientError()) {
            return "Niepoprawne żądanie.";
        }
        return "Wystąpił nieoczekiwany błąd.";
    }
}
