package pl.przychodnia360.identity.infrastructure;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Konfiguracja tokenów (security.jwt.*), podpis RS256 (ADR-M04). Klucze są w formacie Base64: {@code private-key}
 * to PKCS#8 (DER), {@code public-key} to X.509 (DER). Klucz aktywny ({@code active-kid}) musi mieć klucz prywatny
 * (publiczny jest z niego wyprowadzany); klucze wycofywane po rotacji wystarczy podać wyłącznie publicznie
 * (weryfikują stare tokeny, ale niczego nie podpisują). {@code generate-ephemeral-key} (tylko profile local i test)
 * generuje klucz przy starcie, gdy żaden nie jest skonfigurowany; w profilu demo brak kluczy zatrzymuje start.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String activeKid,
        Map<String, Key> keys,
        @DefaultValue("false") boolean generateEphemeralKey,
        @DefaultValue("przychodnia360") String issuer,
        @DefaultValue("PT10M") Duration accessTtl,
        @DefaultValue("P7D") Duration refreshTtl) {

    /** Para kluczy jednego identyfikatora {@code kid}; co najmniej jeden element musi być podany. */
    public record Key(String privateKey, String publicKey) {
    }
}
