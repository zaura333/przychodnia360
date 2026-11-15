package pl.przychodnia360.identity.application;

/** Haszowanie haseł (Argon2id, ADR-B06). */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);

    /** Weryfikacja wobec atrapy hasha: ten sam koszt czasowy, gdy użytkownik nie istnieje (ADR-B02). */
    void burnTime(String rawPassword);

    boolean needsRehash(String hash);
}
