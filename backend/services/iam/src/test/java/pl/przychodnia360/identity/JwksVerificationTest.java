package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pl.przychodnia360.AbstractApiTest;
import pl.przychodnia360.common.security.AccessRevocations;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.JwksKeyResolver;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.common.security.RsaAccessTokenVerifier;
import pl.przychodnia360.identity.domain.User;
import tools.jackson.databind.JsonNode;

/**
 * Fala 1.2: usługa inna niż iam weryfikuje tokeny wyłącznie kluczami pobranymi z JWKS (bez sekretu i bez klucza
 * prywatnego). Test używa tego samego kodu co {@code JwksSecurityConfig}, skierowanego na działający endpoint.
 */
class JwksVerificationTest extends AbstractApiTest {

    private RsaAccessTokenVerifier remoteVerifier() {
        Clock clock = Clock.systemUTC();
        return new RsaAccessTokenVerifier(new JwksKeyResolver("http://localhost:" + port + "/api/v1/auth/jwks", 3000),
                "przychodnia360", clock, new AccessRevocations(clock, Duration.ofMinutes(10)));
    }

    @Test
    void jwksEndpointIsPublicAndExposesOnlyPublicKeys() {
        Resp response = call("GET", "/api/v1/auth/jwks", null, null);

        assertThat(response.status()).isEqualTo(200);
        JsonNode keys = response.json().get("keys");
        assertThat(keys).isNotEmpty();
        keys.forEach(key -> {
            assertThat(key.get("kty").asString()).isEqualTo("RSA");
            assertThat(key.get("alg").asString()).isEqualTo("RS256");
            assertThat(key.get("kid").asString()).isNotBlank();
            assertThat(key.has("d")).as("brak parametrów prywatnych").isFalse();
        });
    }

    @Test
    void anotherServiceVerifiesAnIssuedTokenUsingOnlyTheJwks() {
        UUID tenant = newTenant();
        User doctor = newUser(tenant, Role.DOCTOR);
        String token = login(tenant, doctor);

        AuthenticatedUser verified = remoteVerifier().verify(token).orElseThrow();

        assertThat(verified.userId()).isEqualTo(doctor.id());
        assertThat(verified.tenantId()).isEqualTo(tenant);
        assertThat(verified.role()).isEqualTo(Role.DOCTOR);
    }

    @Test
    void remoteVerifierRejectsATokenSignedByAForeignKeyEvenWithAKnownKid() throws Exception {
        String kid = call("GET", "/api/v1/auth/jwks", null, null).json().get("keys").get(0).get("kid").asString();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair attacker = generator.generateKeyPair();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("przychodnia360").subject(UUID.randomUUID().toString())
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .claim("role", "PLATFORM_ADMIN").build();
        SignedJWT forged = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).build(), claims);
        forged.sign(new RSASSASigner((RSAPrivateKey) attacker.getPrivate()));

        assertThat(remoteVerifier().verify(forged.serialize())).isEmpty();
    }

    @Test
    void remoteVerifierRejectsAnUnknownKid() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair other = generator.generateKeyPair();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("przychodnia360").subject(UUID.randomUUID().toString())
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .claim("role", "DOCTOR").build();
        SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("nieznany").build(), claims);
        token.sign(new RSASSASigner((RSAPrivateKey) other.getPrivate()));

        assertThat(remoteVerifier().verify(token.serialize())).isEmpty();
    }
}
