package pl.przychodnia360.shared;

/** Bazowy wyjątek domenowy. Komunikat jest bezpieczny do pokazania użytkownikowi (bez danych wrażliwych i SQL). */
public abstract class DomainException extends RuntimeException {

    private final String problemType;

    protected DomainException(String problemType, String message) {
        super(message);
        this.problemType = problemType;
    }

    /** Ostatni segment typu Problem Details, np. {@code slot-taken}. */
    public String problemType() {
        return problemType;
    }
}
