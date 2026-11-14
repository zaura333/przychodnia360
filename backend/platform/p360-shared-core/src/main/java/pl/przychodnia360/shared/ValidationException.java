package pl.przychodnia360.shared;

import java.util.List;

public class ValidationException extends DomainException {

    /** Błąd pojedynczego pola. */
    public record FieldError(String field, String message) {
    }

    private final List<FieldError> errors;

    public ValidationException(String message) {
        this(message, List.of());
    }

    public ValidationException(String message, List<FieldError> errors) {
        super("validation", message);
        this.errors = List.copyOf(errors);
    }

    public List<FieldError> errors() {
        return errors;
    }
}
