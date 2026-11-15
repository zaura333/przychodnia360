package pl.przychodnia360.common.security.jwt;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Konfiguracja tokenów (security.jwt.*). Klucze HS256 w Base64 (min. 32 bajty); kilka kluczy naraz umożliwia rotację:
 * podpisuje activeKid, weryfikować można każdym z kluczy.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String activeKid,
        Map<String, String> keys,
        @DefaultValue("przychodnia360") String issuer,
        @DefaultValue("PT10M") Duration accessTtl,
        @DefaultValue("P7D") Duration refreshTtl) {
}
