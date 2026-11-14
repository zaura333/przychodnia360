package pl.przychodnia360.shared;

/** Żądanie poprawne składniowo, ale niezgodne z regułami biznesowymi (422), np. termin w przeszłości. */
public class UnprocessableException extends DomainException {

    public UnprocessableException(String problemType, String message) {
        super(problemType, message);
    }
}
