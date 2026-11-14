package pl.przychodnia360.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.identity.api.AuthenticatedUser;
import pl.przychodnia360.identity.api.Role;

class JwtServiceTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString("key-a-0123456789-0123456789-0123456".getBytes());
    private static final String KEY_B = Base64.getEncoder().encodeToString("key-b-0123456789-0123456789-0123456".getBytes());
    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");

    private static JwtService service(String activeKid, Map<String, String> keys, Instant now) {
        return new JwtService(new JwtProperties(activeKid, keys, "przychodnia360", Duration.ofMinutes(10),
                Duration.ofDays(7)), Clock.fixed(now, ZoneOffset.UTC));
    }

    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(),
            Role.DOCTOR, UUID.randomUUID(), null);

    @Test
    void roundTripPreservesClaims() {
        JwtService jwt = service("a", Map.of("a", KEY_A), NOW);
        assertThat(jwt.verify(jwt.issue(user))).contains(user);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service("a", Map.of("a", KEY_A), NOW).issue(user);
        JwtService later = service("a", Map.of("a", KEY_A), NOW.plus(Duration.ofMinutes(10)));
        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void rotationOldKeyStillVerifiesButNewTokensUseActiveKey() {
        String oldToken = service("a", Map.of("a", KEY_A), NOW).issue(user);
        JwtService rotated = service("b", Map.of("a", KEY_A, "b", KEY_B), NOW);
        assertThat(rotated.verify(oldToken)).contains(user);
        assertThat(service("a", Map.of("a", KEY_A), NOW).verify(rotated.issue(user))).isEmpty();
    }

    @Test
    void unknownKidWrongAlgorithmAndGarbageAreRejected() throws Exception {
        JwtService jwt = service("a", Map.of("a", KEY_A), NOW);
        assertThat(jwt.verify("not-a-jwt")).isEmpty();
        assertThat(jwt.verify("")).isEmpty();

        SignedJWT hs512 = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS512).keyID("a").build(),
                new JWTClaimsSet.Builder().issuer("przychodnia360").subject(user.userId().toString())
                        .expirationTime(Date.from(NOW.plusSeconds(60))).claim("role", "DOCTOR").build());
        hs512.sign(new MACSigner(new byte[64]));
        assertThat(jwt.verify(hs512.serialize())).as("algorytm inny niż HS256").isEmpty();

        String otherKid = service("b", Map.of("b", KEY_B), NOW).issue(user);
        assertThat(jwt.verify(otherKid)).isEmpty();
    }

    @Test
    void rejectsShortKeysAndMissingActiveKey() {
        String shortKey = Base64.getEncoder().encodeToString("short".getBytes());
        assertThatThrownBy(() -> service("a", Map.of("a", shortKey), NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service("x", Map.of("a", KEY_A), NOW)).isInstanceOf(IllegalStateException.class);
    }
}
