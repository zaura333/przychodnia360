package pl.przychodnia360.shared;

/** Przekroczony limit żądań (429). */
public class TooManyRequestsException extends DomainException {

    public TooManyRequestsException(String message) {
        super("too-many-requests", message);
    }
}
