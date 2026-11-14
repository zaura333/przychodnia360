package pl.przychodnia360.testsupport;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;
import pl.przychodnia360.RecordingEventTransport;

/**
 * Baza testów integracyjnych usług (p360-test-support). Jeden współdzielony kontener PostgreSQL (singleton).
 * Kontener startuje z tym samym skryptem init co docker-compose (docker/postgres/init/01-roles.sh),
 * Flyway działa jako p360_owner, a aplikacja jako p360_app.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(RecordingEventTransport.Config.class)
public abstract class PostgresIntegrationTest {

    /** Wersja musi być równa wersji w docker-compose.yml i w ADR (pilnuje tego ComposeConsistencyTest, zadanie DK3). */
    public static final String POSTGRES_IMAGE = "postgres:16.6";

    /**
     * Tryb usługi: gdy własność systemowa {@code p360.test.service} (np. {@code catalog}) jest ustawiona, testy działają
     * na bazie {@code p360_<usługa>} z rolami {@code p360_<usługa>_owner} i {@code p360_<usługa>_app}, utworzonymi przez
     * ten sam skrypt init co w compose (P360_SERVICE_DATABASES). Bez niej: baza {@code p360}, role {@code p360_owner}
     * i {@code p360_app} (clinic-core).
     */
    private static final String SERVICE = System.getProperty("p360.test.service", "").trim();
    private static final String IDENT = SERVICE.replace('-', '_');

    protected static final String DB_NAME = SERVICE.isEmpty() ? "p360" : "p360_" + IDENT;
    protected static final String OWNER_ROLE = SERVICE.isEmpty() ? "p360_owner" : "p360_" + IDENT + "_owner";
    protected static final String APP_ROLE = SERVICE.isEmpty() ? "p360_app" : "p360_" + IDENT + "_app";
    protected static final String OWNER_PASSWORD = "p360_owner_test";
    protected static final String APP_PASSWORD = "p360_app_test";

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withEnv("P360_DB_NAME", SERVICE.isEmpty() ? DB_NAME : "p360")
            .withEnv("P360_OWNER_PASSWORD", OWNER_PASSWORD)
            .withEnv("P360_SERVICE_DATABASES", SERVICE)
            .withEnv("P360_" + IDENT.toUpperCase(java.util.Locale.ROOT) + "_OWNER_PASSWORD", OWNER_PASSWORD)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(initScript(), 0755),
                    "/docker-entrypoint-initdb.d/01-roles.sh")
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\s", 2));

    static {
        POSTGRES.start();
    }

    @Value("${local.server.port}")
    protected int port;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = jdbcUrl();
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> OWNER_ROLE);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
        registry.add("spring.flyway.placeholders.app_password", () -> APP_PASSWORD);
        registry.add("spring.flyway.placeholders.app_role", () -> APP_ROLE);
        registry.add("spring.flyway.placeholders.owner_role", () -> OWNER_ROLE);
    }

    protected static String jdbcUrl() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + DB_NAME;
    }

    protected static String appPassword() {
        return APP_PASSWORD;
    }

    protected HttpResponse<String> get(String path, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Szuka katalogu docker/postgres/init idąc w górę od katalogu roboczego (Maven, IDE). */
    private static Path initScript() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("docker/postgres/init/01-roles.sh");
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Nie znaleziono docker/postgres/init/01-roles.sh");
    }
}
