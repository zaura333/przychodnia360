package pl.przychodnia360.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.AbstractIntegrationTest;
import pl.przychodnia360.identity.api.Role;
import pl.przychodnia360.identity.application.PasswordHasher;
import pl.przychodnia360.identity.application.UserRepository;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;

/** Logowanie, /me, odświeżanie z rotacją, ponowne użycie tokenu, blokada konta, wylogowanie, Origin. */
class AuthFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct horse battery";
    private static final Pattern ACCESS = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcClient jdbc;

    private String slug;
    private UUID tenantId;
    private String email;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        slug = "auth-" + suffix;
        email = "jan." + suffix + "@example.test";
        tenantId = UUID.randomUUID();
        try (var h = TenantContext.openWithoutTenant()) {
            tx.executeWithoutResult(s -> jdbc.sql("INSERT INTO tenants (id, slug, name) VALUES (?, ?, ?)")
                    .params(tenantId, slug, "Klinika " + suffix).update());
        }
        try (var h = TenantContext.open(new TenantId(tenantId))) {
            tx.executeWithoutResult(s -> users.insert(
                    User.create(Ids.newId(), tenantId, email, Role.RECEPTIONIST, hasher.hash(PASSWORD))));
        }
    }

    private HttpResponse<String> post(String path, String json, String... headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json));
        if (headers.length > 0) {
            b.headers(headers);
        }
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> login(String password) throws Exception {
        return post("/api/v1/auth/login", "{\"tenantSlug\":\"" + slug + "\",\"email\":\"" + email
                + "\",\"password\":\"" + password + "\"}");
    }

    private static String refreshCookie(HttpResponse<?> response) {
        return response.headers().allValues("set-cookie").stream().filter(c -> c.startsWith("p360_rt="))
                .findFirst().orElseThrow();
    }

    private static String cookieValue(String setCookie) {
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private static String access(HttpResponse<String> response) {
        Matcher m = ACCESS.matcher(response.body());
        assertThat(m.find()).as(response.body()).isTrue();
        return m.group(1);
    }

    @Test
    void loginReturnsAccessTokenAndHardenedRefreshCookie() throws Exception {
        HttpResponse<String> response = login(PASSWORD);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("refresh");
        String cookie = refreshCookie(response);
        assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth");

        HttpResponse<String> me = get("/api/v1/me", "Authorization", "Bearer " + access(response));
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"role\":\"RECEPTIONIST\"").contains(tenantId.toString());
    }

    @Test
    void allLoginFailuresLookTheSame() throws Exception {
        HttpResponse<String> wrongPassword = login("wrong password here");
        HttpResponse<String> unknownUser = post("/api/v1/auth/login", "{\"tenantSlug\":\"" + slug
                + "\",\"email\":\"nobody@example.test\",\"password\":\"whatever whatever\"}");
        HttpResponse<String> unknownTenant = post("/api/v1/auth/login",
                "{\"tenantSlug\":\"no-such-clinic\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");

        for (HttpResponse<String> r : new HttpResponse[] {wrongPassword, unknownUser, unknownTenant}) {
            assertThat(r.statusCode()).isEqualTo(401);
            assertThat(r.body()).contains("/problems/invalid-credentials").doesNotContain("slug");
        }
        assertThat(wrongPassword.body().replaceAll("\"traceId\":\"[^\"]+\"", ""))
                .isEqualTo(unknownUser.body().replaceAll("\"traceId\":\"[^\"]+\"", ""))
                .isEqualTo(unknownTenant.body().replaceAll("\"traceId\":\"[^\"]+\"", ""));
    }

    @Test
    void accountIsLockedAfterRepeatedFailuresEvenWithCorrectPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(login("wrong password " + i).statusCode()).isEqualTo(401);
        }
        assertThat(login(PASSWORD).statusCode()).as("zablokowane konto odrzuca poprawne hasło").isEqualTo(401);
    }

    @Test
    void refreshRotatesTokenAndReuseRevokesTheWholeFamily() throws Exception {
        String first = cookieValue(refreshCookie(login(PASSWORD)));

        HttpResponse<String> rotated = post("/api/v1/auth/refresh", "", "Cookie", first);
        assertThat(rotated.statusCode()).isEqualTo(200);
        String second = cookieValue(refreshCookie(rotated));
        assertThat(second).isNotEqualTo(first);

        HttpResponse<String> reuse = post("/api/v1/auth/refresh", "", "Cookie", first);
        assertThat(reuse.statusCode()).isEqualTo(401);

        HttpResponse<String> afterReuse = post("/api/v1/auth/refresh", "", "Cookie", second);
        assertThat(afterReuse.statusCode()).as("rodzina unieważniona po ponownym użyciu").isEqualTo(401);
    }

    @Test
    void refreshRejectsMissingMalformedAndForeignOriginRequests() throws Exception {
        assertThat(post("/api/v1/auth/refresh", "").statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/auth/refresh", "", "Cookie", "p360_rt=garbage").statusCode()).isEqualTo(401);

        String cookie = cookieValue(refreshCookie(login(PASSWORD)));
        HttpResponse<String> foreign = post("/api/v1/auth/refresh", "", "Cookie", cookie, "Origin", "https://evil.example");
        assertThat(foreign.statusCode()).isEqualTo(403);
        HttpResponse<String> allowed = post("/api/v1/auth/refresh", "", "Cookie", cookie, "Origin", "http://localhost:5173");
        assertThat(allowed.statusCode()).isEqualTo(200);
    }

    @Test
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        HttpResponse<String> login = login(PASSWORD);
        String cookie = cookieValue(refreshCookie(login));

        HttpResponse<String> logout = post("/api/v1/auth/logout", "", "Authorization", "Bearer " + access(login),
                "Cookie", cookie);
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(refreshCookie(logout)).contains("Max-Age=0");
        assertThat(post("/api/v1/auth/refresh", "", "Cookie", cookie).statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/auth/logout", "").statusCode()).as("wylogowanie wymaga tokenu").isEqualTo(401);
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = access(login(PASSWORD));
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThat(get("/api/v1/me", "Authorization", "Bearer " + tampered).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/me").statusCode()).isEqualTo(401);
    }

    @Test
    void platformAdminLogsInWithoutTenant() throws Exception {
        String platformEmail = "root." + UUID.randomUUID().toString().substring(0, 8) + "@platform.test";
        try (var h = TenantContext.openPlatformScope()) {
            tx.executeWithoutResult(s -> users.insert(
                    User.create(Ids.newId(), null, platformEmail, Role.PLATFORM_ADMIN, hasher.hash(PASSWORD))));
        }
        HttpResponse<String> response = post("/api/v1/auth/platform/login",
                "{\"email\":\"" + platformEmail + "\",\"password\":\"" + PASSWORD + "\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(refreshCookie(response)).contains("Path=/api/v1/auth");

        HttpResponse<String> me = get("/api/v1/me", "Authorization", "Bearer " + access(response));
        assertThat(me.body()).contains("\"role\":\"PLATFORM_ADMIN\"").contains("\"tenantId\":null");

        String cookie = cookieValue(refreshCookie(response));
        assertThat(post("/api/v1/auth/refresh", "", "Cookie", cookie).statusCode()).isEqualTo(200);
        // zwykły login placówki nie loguje administratora platformy
        assertThat(post("/api/v1/auth/login", "{\"tenantSlug\":\"" + slug + "\",\"email\":\"" + platformEmail
                + "\",\"password\":\"" + PASSWORD + "\"}").statusCode()).isEqualTo(401);
    }
}
