package pl.przychodnia360.shared;

/** Zależna usługa jest niedostępna (limit czasu, błąd 5xx, otwarty obwód): 503, operacja może być powtórzona. */
public class ServiceUnavailableException extends DomainException {

    public ServiceUnavailableException(String message) {
        super("dependency-unavailable", message);
    }
}
