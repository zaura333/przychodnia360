package pl.przychodnia360.shared;

public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super("conflict", message);
    }

    public ConflictException(String problemType, String message) {
        super(problemType, message);
    }
}
