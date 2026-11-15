package pl.przychodnia360.identity.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.common.security.AccessRevocations;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;

/** Tokeny RS256 (ADR-M04): wystawianie, weryfikacja, rotacja kluczy, JWKS i odporność na ataki na algorytm. */
class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static KeyPair keyA;
    private static KeyPair keyB;
    private static KeyPair foreign;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyA = generator.generateKeyPair();
        keyB = generator.generateKeyPair();
        foreign = generator.generateKeyPair();
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static JwtProperties.Key privateOf(KeyPair pair) {
        return new JwtProperties.Key(b64(pair.getPrivate().getEncoded()), null);
    }

    private static JwtProperties.Key publicOnlyKey(byte[] x509) {
        return new JwtProperties.Key(null, b64(x509));
    }

    private static JwtProperties.Key publicOnly(KeyPair pair) {
        return new JwtProperties.Key(null, b64(pair.getPublic().getEncoded()));
    }

    private static JwtService service(String activeKid, Map<String, JwtProperties.Key> keys, Instant now,
            boolean ephemeral, AccessRevocations revocations) {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        return new JwtService(new JwtProperties(activeKid, keys, ephemeral, "przychodnia360", Duration.ofMinutes(10),
                Duration.ofDays(7)), clock, revocations);
    }

    private static JwtService service(String activeKid, Map<String, JwtProperties.Key> keys, Instant now) {
        return service(activeKid, keys, now, false, new AccessRevocations(Clock.fixed(now, ZoneOffset.UTC),
                Duration.ofMinutes(10)));
    }

    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(),
            Role.DOCTOR, UUID.randomUUID(), null);

    @Test
    void roundTripPreservesClaims() {
        JwtService jwt = service("a", Map.of("a", privateOf(keyA)), NOW);
        assertThat(jwt.verify(jwt.issue(user))).contains(user);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service("a", Map.of("a", privateOf(keyA)), NOW).issue(user);
        JwtService later = service("a", Map.of("a", privateOf(keyA)), NOW.plus(Duration.ofMinutes(10)));
        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void rotationOldKeyStillVerifiesButNewTokensUseActiveKey() {
        String oldToken = service("a", Map.of("a", privateOf(keyA)), NOW).issue(user);
        // po rotacji: aktywny b, a wycofywany a zostaje tylko jako klucz publiczny
        JwtService rotated = service("b", Map.of("a", publicOnly(keyA), "b", privateOf(keyB)), NOW);
        assertThat(rotated.verify(oldToken)).contains(user);
        String newToken = rotated.issue(user);
        assertThat(service("a", Map.of("a", privateOf(keyA)), NOW).verify(newToken))
                .as("usługa znająca tylko stary klucz nie zna nowego kid").isEmpty();
    }

    @Test
    void retiredPublicOnlyKeyCannotBeTheActiveSigningKey() {
        assertThatThrownBy(() -> service("a", Map.of("a", publicOnly(keyA)), NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("klucza prywatnego");
    }

    @Test
    void garbageAndUnknownKeysAreRejected() {
        JwtService jwt = service("a", Map.of("a", privateOf(keyA)), NOW);
        assertThat(jwt.verify("not-a-jwt")).isEmpty();
        assertThat(jwt.verify("")).isEmpty();

        String otherKid = service("b", Map.of("b", privateOf(keyB)), NOW).issue(user);
        assertThat(jwt.verify(otherKid)).as("nieznany kid").isEmpty();
    }

    @Test
    void tokenSignedByAnAttackerWithTheSameKidIsRejected() throws Exception {
        JwtService jwt = service("a", Map.of("a", privateOf(keyA)), NOW);
        SignedJWT forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("a").build(), claims());
        forged.sign(new RSASSASigner((RSAPrivateKey) foreign.getPrivate()));
        assertThat(jwt.verify(forged.serialize())).as("podpis cudzym kluczem").isEmpty();
    }

    @Test
    void algorithmConfusionAndUnsignedTokensAreRejected() throws Exception {
        JwtService jwt = service("a", Map.of("a", privateOf(keyA)), NOW);

        // klasyczny atak: token HS256 podpisany bajtami klucza PUBLICZNEGO jako sekretem HMAC
        byte[] publicKeyBytes = ((RSAPublicKey) keyA.getPublic()).getEncoded();
        byte[] secret = new byte[Math.max(32, publicKeyBytes.length)];
        System.arraycopy(publicKeyBytes, 0, secret, 0, publicKeyBytes.length);
        SignedJWT hs256 = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("a").build(), claims());
        hs256.sign(new MACSigner(secret));
        assertThat(jwt.verify(hs256.serialize())).as("HS256 podpisany kluczem publicznym").isEmpty();

        SignedJWT rs512 = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS512).keyID("a").build(), claims());
        rs512.sign(new RSASSASigner((RSAPrivateKey) keyA.getPrivate()));
        assertThat(jwt.verify(rs512.serialize())).as("inny algorytm niż RS256").isEmpty();

        PlainJWT none = new PlainJWT(new PlainHeader(), claims());
        assertThat(jwt.verify(none.serialize())).as("alg none").isEmpty();
    }

    @Test
    void wrongIssuerAndMissingIssuedAtAreRejected() throws Exception {
        JwtService jwt = service("a", Map.of("a", privateOf(keyA)), NOW);
        JWTClaimsSet wrongIssuer = new JWTClaimsSet.Builder().issuer("ktos-inny").subject(user.userId().toString())
                .issueTime(Date.from(NOW)).expirationTime(Date.from(NOW.plusSeconds(60))).claim("role", "DOCTOR")
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("a").build(), wrongIssuer);
        token.sign(new RSASSASigner((RSAPrivateKey) keyA.getPrivate()));
        assertThat(jwt.verify(token.serialize())).as("zły wystawca").isEmpty();

        JWTClaimsSet noIssuedAt = new JWTClaimsSet.Builder().issuer("przychodnia360")
                .subject(user.userId().toString()).expirationTime(Date.from(NOW.plusSeconds(60)))
                .claim("role", "DOCTOR").build();
        SignedJWT noIat = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("a").build(), noIssuedAt);
        noIat.sign(new RSASSASigner((RSAPrivateKey) keyA.getPrivate()));
        assertThat(jwt.verify(noIat.serialize())).as("brak iat").isEmpty();
    }

    @Test
    void revokedUserTokenIsRejected() {
        AccessRevocations revocations = new AccessRevocations(CLOCK, Duration.ofMinutes(10));
        JwtService jwt = service("a", Map.of("a", privateOf(keyA)), NOW, false, revocations);
        String token = jwt.issue(user);
        assertThat(jwt.verify(token)).contains(user);
        revocations.revoke(user.userId());
        assertThat(jwt.verify(token)).isEmpty();
    }

    @Test
    void missingKeysFailFastUnlessEphemeralKeyIsAllowed() {
        assertThatThrownBy(() -> service(null, Map.of(), NOW)).isInstanceOf(IllegalStateException.class);

        JwtService ephemeral = service(null, Map.of(), NOW, true, new AccessRevocations(CLOCK, Duration.ofMinutes(10)));
        assertThat(ephemeral.verify(ephemeral.issue(user))).contains(user);
        assertThat(ephemeral.jwks().get("keys")).asList().hasSize(1);
    }

    @Test
    void invalidAndTooShortKeysAreRejected() throws Exception {
        assertThatThrownBy(() -> service("a", Map.of("a", new JwtProperties.Key("nie-base64!!", null)), NOW))
                .isInstanceOf(IllegalStateException.class);
        // Zbyt krótki klucz: publiczny klucz RSA o module 1024 bitów zbudowany z liczb, bez generowania pary kluczy
        // (generowanie słabego klucza jest zgłaszane przez CodeQL; walidacja w JwtService sprawdza długość modułu).
        BigInteger weakModulus = BigInteger.ONE.shiftLeft(1023).setBit(0);
        var weakPublic = KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(weakModulus, BigInteger.valueOf(65537)));
        assertThat(((RSAPublicKey) weakPublic).getModulus().bitLength()).isEqualTo(1024);
        assertThatThrownBy(() -> service("a", Map.of("a", publicOnlyKey(weakPublic.getEncoded())), NOW))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("2048");
    }

    @Test
    void jwksPublishesOnlyPublicParametersForActiveAndRetiredKeys() throws Exception {
        JwtService jwt = service("b", Map.of("a", publicOnly(keyA), "b", privateOf(keyB)), NOW);
        String json = JSONObjectUtils.toJSONString(jwt.jwks());
        JWKSet set = JWKSet.parse(json);

        assertThat(set.getKeys()).extracting(k -> k.getKeyID()).containsExactlyInAnyOrder("a", "b");
        assertThat(set.getKeys()).allSatisfy(k -> {
            assertThat(k.isPrivate()).as("klucz %s jest publiczny", k.getKeyID()).isFalse();
            assertThat(k.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        });
        assertThat(json).doesNotContain("\"d\"", "\"p\"", "\"q\"", "\"dp\"", "\"dq\"", "\"qi\"");
    }

    private JWTClaimsSet claims() {
        return new JWTClaimsSet.Builder().issuer("przychodnia360").subject(user.userId().toString())
                .issueTime(Date.from(NOW)).expirationTime(Date.from(NOW.plusSeconds(60)))
                .claim("role", "DOCTOR").claim("tid", user.tenantId().toString()).build();
    }
}
