package pl.przychodnia360.identity.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import pl.przychodnia360.identity.application.PasswordHasher;

/** Argon2id; parametry w konfiguracji (security.password.argon2.*), domyślnie zalecenia OWASP (ADR-B06). */
@Component
class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2PasswordEncoder encoder;
    private final String dummyHash;
    private final String currentPrefix;

    Argon2PasswordHasher(
            @Value("${security.password.argon2.memory-kib:19456}") int memoryKib,
            @Value("${security.password.argon2.iterations:2}") int iterations,
            @Value("${security.password.argon2.parallelism:1}") int parallelism) {
        this.encoder = new Argon2PasswordEncoder(16, 32, parallelism, memoryKib, iterations);
        this.dummyHash = encoder.encode("dummy-password-for-timing");
        this.currentPrefix = "$argon2id$v=19$m=" + memoryKib + ",t=" + iterations + ",p=" + parallelism + "$";
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return hash != null && encoder.matches(rawPassword, hash);
    }

    @Override
    public void burnTime(String rawPassword) {
        encoder.matches(rawPassword, dummyHash);
    }

    @Override
    public boolean needsRehash(String hash) {
        return !hash.startsWith(currentPrefix);
    }
}
