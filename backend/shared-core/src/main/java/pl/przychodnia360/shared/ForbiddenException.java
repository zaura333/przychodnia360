package pl.przychodnia360.shared;

public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super("forbidden", message);
    }
}
