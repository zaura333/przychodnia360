package pl.przychodnia360.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Clock;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Weryfikacja tokenów RS256: algorytm musi być RS256 (odrzuca none, HS* i inne, więc nie ma ataku z podmianą
 * algorytmu), klucz publiczny wynika z {@code kid} (dostarcza go {@code keys}), a następnie sprawdzane są
 * podpis, wystawca, ważność i lista odwołań ({@link AccessRevocations}).
 */
public class RsaAccessTokenVerifier implements AccessTokenVerifier {

    private final Function<String, RSAPublicKey> keys;
    private final String issuer;
    private final Clock clock;
    private final AccessRevocations revocations;
    private final String audience;

    /** {@code keys} zwraca klucz publiczny dla {@code kid} albo null, gdy klucz jest nieznany. Bez tokenów usługowych. */
    public RsaAccessTokenVerifier(Function<String, RSAPublicKey> keys, String issuer, Clock clock,
            AccessRevocations revocations) {
        this(keys, issuer, clock, revocations, null);
    }

    /**
     * {@code audience} to nazwa tej usługi: token usługowy (rola SERVICE) jest przyjmowany tylko wtedy, gdy jego
     * {@code aud} ją zawiera; bez nazwy usługi tokeny usługowe są odrzucane (np. w iam, który ich nie przyjmuje).
     */
    public RsaAccessTokenVerifier(Function<String, RSAPublicKey> keys, String issuer, Clock clock,
            AccessRevocations revocations, String audience) {
        this.keys = keys;
        this.issuer = issuer;
        this.clock = clock;
        this.revocations = revocations;
        this.audience = audience;
    }

    @Override
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            String kid = jwt.getHeader().getKeyID();
            RSAPublicKey key = kid == null ? null : keys.apply(kid);
            if (key == null || !jwt.verify(new RSASSAVerifier(key))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            Date issued = claims.getIssueTime();
            if (!issuer.equals(claims.getIssuer()) || exp == null || issued == null
                    || !exp.toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(claims.getSubject());
            if (revocations.isRevoked(userId, issued.toInstant())) {
                return Optional.empty();
            }
            Role role = Role.valueOf(claims.getStringClaim("role"));
            if (role == Role.SERVICE && (audience == null || claims.getAudience() == null
                    || !claims.getAudience().contains(audience) || uuid(claims, "tid") == null)) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedUser(userId, uuid(claims, "tid"), role, uuid(claims, "eid"),
                    uuid(claims, "pid")));
        } catch (ParseException | com.nimbusds.jose.JOSEException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static UUID uuid(JWTClaimsSet claims, String name) throws ParseException {
        String value = claims.getStringClaim(name);
        return value == null ? null : UUID.fromString(value);
    }
}
