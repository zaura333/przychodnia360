package pl.przychodnia360.identity.infrastructure;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pl.przychodnia360.common.security.AccessRevocations;
import pl.przychodnia360.common.security.AccessTokenVerifier;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.RsaAccessTokenVerifier;

/**
 * Wystawianie i weryfikacja tokenów dostępu (JWT RS256, nagłówek kid, rotacja kluczy). Tylko iam ma klucz
 * prywatny; klucze publiczne są publikowane w JWKS ({@link #jwks()}), więc inne usługi weryfikują tokeny bez
 * sekretu (ADR-M04).
 */
@Component
public class JwtService implements AccessTokenVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_KEY_BITS = 2048;

    private record KeyMaterial(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
    }

    private final JwtProperties properties;
    private final Clock clock;
    private final Map<String, KeyMaterial> keys = new LinkedHashMap<>();
    private final String activeKid;
    private final RsaAccessTokenVerifier verifier;

    JwtService(JwtProperties properties, Clock clock, AccessRevocations revocations) {
        this.properties = properties;
        this.clock = clock;
        boolean configured = properties.keys() != null && !properties.keys().isEmpty();
        if (configured) {
            properties.keys().forEach((kid, key) -> keys.put(kid, load(kid, key)));
            this.activeKid = properties.activeKid();
        } else if (properties.generateEphemeralKey()) {
            this.activeKid = "ephemeral-" + HexFormat.of().formatHex(randomBytes(4));
            keys.put(activeKid, generate());
            LOG.warn("Brak kluczy JWT (security.jwt.keys): wygenerowano tymczasowy klucz {} (tylko local i test; "
                    + "tokeny przestaną być ważne po restarcie)", activeKid);
        } else {
            throw new IllegalStateException("Brak kluczy JWT (security.jwt.keys)");
        }
        KeyMaterial active = keys.get(activeKid);
        if (active == null || active.privateKey() == null) {
            throw new IllegalStateException("Brak klucza prywatnego dla aktywnego klucza JWT: " + activeKid);
        }
        this.verifier = new RsaAccessTokenVerifier(
                kid -> keys.containsKey(kid) ? keys.get(kid).publicKey() : null,
                properties.issuer(), clock, revocations);
    }

    public String issue(AuthenticatedUser user) {
        Instant now = clock.instant();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(properties.issuer())
                .subject(user.userId().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(properties.accessTtl())))
                .claim("role", user.role().name());
        if (user.tenantId() != null) {
            claims.claim("tid", user.tenantId().toString());
        }
        if (user.employeeId() != null) {
            claims.claim("eid", user.employeeId().toString());
        }
        if (user.patientId() != null) {
            claims.claim("pid", user.patientId().toString());
        }
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT)
                .keyID(activeKid).build();
        try {
            SignedJWT jwt = new SignedJWT(header, claims.build());
            jwt.sign(new RSASSASigner(keys.get(activeKid).privateKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Nie udało się podpisać tokenu", e);
        }
    }

    /** Pusty wynik dla każdego niepoprawnego tokenu (zły podpis, algorytm, kid, wystawca, wygasły, odwołany). */
    @Override
    public Optional<AuthenticatedUser> verify(String token) {
        return verifier.verify(token);
    }

    /** Zestaw kluczy publicznych (JWKS): wyłącznie parametry publiczne, także dla kluczy wycofywanych. */
    public Map<String, Object> jwks() {
        var list = keys.entrySet().stream()
                .map(e -> (com.nimbusds.jose.jwk.JWK) new RSAKey.Builder(e.getValue().publicKey())
                        .keyID(e.getKey()).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build())
                .toList();
        return new JWKSet(list).toJSONObject(true);
    }

    public Duration accessTtl() {
        return properties.accessTtl();
    }

    public Duration refreshTtl() {
        return properties.refreshTtl();
    }

    private static KeyMaterial load(String kid, JwtProperties.Key key) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = null;
            RSAPublicKey publicKey = null;
            if (key.privateKey() != null && !key.privateKey().isBlank()) {
                privateKey = (RSAPrivateKey) factory.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.getDecoder().decode(key.privateKey().trim())));
                if (privateKey instanceof RSAPrivateCrtKey crt) {
                    publicKey = (RSAPublicKey) factory.generatePublic(
                            new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
                }
            }
            if (key.publicKey() != null && !key.publicKey().isBlank()) {
                publicKey = (RSAPublicKey) factory.generatePublic(
                        new X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey().trim())));
            }
            if (publicKey == null) {
                throw new IllegalStateException("Klucz JWT " + kid + " nie ma ani klucza prywatnego, ani publicznego");
            }
            BigInteger modulus = publicKey.getModulus();
            if (modulus.bitLength() < MIN_KEY_BITS) {
                throw new IllegalStateException("Klucz JWT " + kid + " jest krótszy niż " + MIN_KEY_BITS + " bitów");
            }
            return new KeyMaterial(publicKey, privateKey);
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("Niepoprawny klucz JWT " + kid + " (oczekiwane Base64: PKCS#8 dla "
                    + "private-key, X.509 dla public-key)", e);
        }
    }

    private static KeyMaterial generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(MIN_KEY_BITS);
            KeyPair pair = generator.generateKeyPair();
            return new KeyMaterial((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Nie udało się wygenerować klucza JWT", e);
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        ThreadLocalRandom.current().nextBytes(bytes);
        return bytes;
    }
}
