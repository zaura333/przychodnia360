package pl.przychodnia360.shared;

public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super("not-found", message);
    }

    protected NotFoundException(String problemType, String message) {
        super(problemType, message);
    }
}
