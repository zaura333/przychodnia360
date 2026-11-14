package pl.przychodnia360.identity.infrastructure;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pl.przychodnia360.common.security.AccessRevocations;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;

/** Wystawianie i weryfikacja tokenów dostępu (JWT HS256, nagłówek kid, rotacja kluczy). */
@Component
public class JwtService {

    private final JwtProperties properties;
    private final Clock clock;
    private final AccessRevocations revocations;
    private final Map<String, byte[]> keys = new HashMap<>();

    JwtService(JwtProperties properties, Clock clock, AccessRevocations revocations) {
        this.properties = properties;
        this.clock = clock;
        this.revocations = revocations;
        if (properties.keys() == null || properties.keys().isEmpty()) {
            throw new IllegalStateException("Brak kluczy JWT (security.jwt.keys)");
        }
        properties.keys().forEach((kid, value) -> {
            byte[] secret = Base64.getDecoder().decode(value);
            if (secret.length < 32) {
                throw new IllegalStateException("Klucz JWT " + kid + " ma mniej niż 32 bajty");
            }
            keys.put(kid, secret);
        });
        if (!keys.containsKey(properties.activeKid())) {
            throw new IllegalStateException("Brak aktywnego klucza JWT: " + properties.activeKid());
        }
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
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT)
                .keyID(properties.activeKid()).build();
        try {
            SignedJWT jwt = new SignedJWT(header, claims.build());
            jwt.sign(new MACSigner(keys.get(properties.activeKid())));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Nie udało się podpisać tokenu", e);
        }
    }

    /** Pusty wynik dla każdego niepoprawnego tokenu (zły podpis, algorytm, kid, wystawca, wygasły). */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            byte[] secret = keys.get(jwt.getHeader().getKeyID());
            if (secret == null || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (!properties.issuer().equals(claims.getIssuer()) || exp == null
                    || !exp.toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(claims.getSubject());
            Date issued = claims.getIssueTime();
            if (issued == null || revocations.isRevoked(userId, issued.toInstant())) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(userId, uuid(claims, "tid"),
                    Role.valueOf(claims.getStringClaim("role")), uuid(claims, "eid"), uuid(claims, "pid")));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static UUID uuid(JWTClaimsSet claims, String name) throws ParseException {
        String value = claims.getStringClaim(name);
        return value == null ? null : UUID.fromString(value);
    }

    public Duration accessTtl() {
        return properties.accessTtl();
    }

    public Duration refreshTtl() {
        return properties.refreshTtl();
    }
}
