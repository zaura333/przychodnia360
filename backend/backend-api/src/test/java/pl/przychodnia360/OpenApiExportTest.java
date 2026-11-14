package pl.przychodnia360;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Generuje kontrakt api/openapi.json z działającej aplikacji (deterministycznie: klucze posortowane, stały
 * serwer i wersja). Plik jest zatwierdzany w repozytorium, a CI sprawdza, że po {@code mvn verify} się nie zmienił
 * (git diff --exit-code). Lokalny {@code mvn verify} aktualizuje plik, który trzeba zatwierdzić.
 */
class OpenApiExportTest extends AbstractIntegrationTest {

    @Test
    void exportsContract() throws Exception {
        var response = get("/api/v1/docs/json");
        assertThat(response.statusCode()).isEqualTo(200);

        String json = response.body().replace("\r\n", "\n");
        if (!json.endsWith("\n")) {
            json += "\n";
        }
        assertThat(json)
                .contains("\"/api/v1/health\"")
                .contains("\"/api/v1/me\"")
                .contains("\"/api/v1/facilities\"");

        Path target = repoRoot().resolve("api/openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, json, StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("docker"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Nie znaleziono korzenia repozytorium");
        }
        return dir;
    }
}
