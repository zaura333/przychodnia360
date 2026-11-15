package pl.przychodnia360;

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
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import pl.przychodnia360.common.security.Role;

/**
 * Wystawca tokenów w testach usługi, która sama nie wystawia tokenów (S1): para kluczy RSA i serwer HTTP z JWKS.
 * Usługa weryfikuje tokeny dokładnie tak jak na produkcji (klucz publiczny z JWKS po {@code kid}), a test nie ma
 * dostępu do żadnego sekretu usługi.
 */
final class TestTokenIssuer {

    private static final String KID = "test-key";
    private static final TestTokenIssuer INSTANCE = new TestTokenIssuer();

    private final RSAPrivateKey privateKey;
    private final HttpServer server;

    private TestTokenIssuer() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            privateKey = (RSAPrivateKey) pair.getPrivate();
            String jwks = new JWKSet(new RSAKey.Builder((RSAPublicKey) pair.getPublic()).keyID(KID)
                    .keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build()).toString();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v1/auth/jwks", exchange -> {
                byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Ustawia adres JWKS wystawcy testowego w środowisku kontekstu (wspólnym dla wszystkich testów usługi). */
    static final class JwksInitializer
            implements org.springframework.context.ApplicationContextInitializer<org.springframework.context.ConfigurableApplicationContext> {
        @Override
        public void initialize(org.springframework.context.ConfigurableApplicationContext context) {
            org.springframework.boot.test.util.TestPropertyValues.of("security.jwt.jwks-url=" + jwksUrl())
                    .applyTo(context.getEnvironment());
        }
    }

    static String jwksUrl() {
        return "http://127.0.0.1:" + INSTANCE.server.getAddress().getPort() + "/api/v1/auth/jwks";
    }

    /** Token dostępu użytkownika o roli w placówce (jak z iam: sub, tid, role, iat, exp). */
    static String token(UUID tenantId, Role role) {
        return token(tenantId, role, UUID.randomUUID());
    }

    /** Token konkretnego użytkownika (znany identyfikator, np. do testu odwołania dostępu). */
    static String token(UUID tenantId, Role role, UUID userId) {
        Instant now = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer("przychodnia360")
                .subject(userId.toString()).issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600))).claim("role", role.name());
        if (tenantId != null) {
            claims.claim("tid", tenantId.toString());
        }
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT)
                    .keyID(KID).build(), claims.build());
            jwt.sign(new RSASSASigner(INSTANCE.privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
