package pl.przychodnia360.shared;

/** Brak lub niepoprawne uwierzytelnienie (401). Komunikat jest celowo ogólny. */
public class UnauthorizedException extends DomainException {

    public UnauthorizedException(String message) {
        super("unauthorized", message);
    }

    public UnauthorizedException(String problemType, String message) {
        super(problemType, message);
    }
}
