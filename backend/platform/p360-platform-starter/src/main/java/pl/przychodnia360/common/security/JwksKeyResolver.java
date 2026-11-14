package pl.przychodnia360.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import java.net.MalformedURLException;
import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.function.Function;

/**
 * Klucze publiczne wystawcy pobierane z JWKS (zwykle {@code /api/v1/auth/jwks} usługi iam). Zestaw jest
 * buforowany (domyślnie 5 minut, odświeżanie z wyprzedzeniem), a nieznany {@code kid} po rotacji kluczy wywołuje
 * ponowne pobranie z ograniczeniem częstotliwości. Gdy wystawca jest niedostępny, ostatni zestaw jest używany
 * dalej, więc krótka awaria iam nie wyloguje użytkowników innych usług.
 */
public class JwksKeyResolver implements Function<String, RSAPublicKey> {

    private final JWKSource<SecurityContext> source;

    public JwksKeyResolver(String jwksUrl, int timeoutMillis) {
        try {
            this.source = JWKSourceBuilder.<SecurityContext>create(URI.create(jwksUrl).toURL(),
                    new DefaultResourceRetriever(timeoutMillis, timeoutMillis))
                    .retrying(true)
                    .outageTolerant(true)
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Niepoprawny adres JWKS: " + jwksUrl, e);
        }
    }

    @Override
    public RSAPublicKey apply(String kid) {
        try {
            List<JWK> matches = source.get(new JWKSelector(new JWKMatcher.Builder().keyID(kid)
                    .keyType(KeyType.RSA).build()), null);
            for (JWK jwk : matches) {
                if (jwk instanceof RSAKey rsa) {
                    return rsa.toRSAPublicKey();
                }
            }
            return null;
        } catch (JOSEException e) {
            return null;
        }
    }
}
