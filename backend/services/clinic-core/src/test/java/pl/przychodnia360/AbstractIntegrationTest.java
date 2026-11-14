package pl.przychodnia360;

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

/**
 * Jeden współdzielony kontener PostgreSQL (singleton) dla wszystkich testów integracyjnych.
 * Kontener startuje z tym samym skryptem init co docker-compose (docker/postgres/init/01-roles.sh),
 * Flyway działa jako p360_owner, a aplikacja jako p360_app.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(RecordingMailSender.Config.class)
public abstract class AbstractIntegrationTest {

    /** Wersja musi być równa wersji w docker-compose.yml i w ADR (pilnuje tego ComposeConsistencyTest, zadanie DK3). */
    public static final String POSTGRES_IMAGE = "postgres:16.6";

    static final String DB_NAME = "p360";
    static final String OWNER_PASSWORD = "p360_owner_test";
    static final String APP_PASSWORD = "p360_app_test";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withEnv("P360_DB_NAME", DB_NAME)
            .withEnv("P360_OWNER_PASSWORD", OWNER_PASSWORD)
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
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.password", () -> OWNER_PASSWORD);
        registry.add("spring.flyway.placeholders.app_password", () -> APP_PASSWORD);
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
