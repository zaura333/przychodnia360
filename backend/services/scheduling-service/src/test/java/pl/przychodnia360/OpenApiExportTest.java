package pl.przychodnia360;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Generuje kontrakt usługi api/services/scheduling-service.openapi.json z działającej aplikacji (deterministycznie: klucze
 * posortowane, stały serwer i wersja). Kontrakt dla frontendu (api/openapi.json) składa z trzech plików usług
 * OpenApiAggregateTest w scheduling-service. CI sprawdza, że po {@code mvn verify} pliki się nie zmieniły.
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
        assertThat(json).contains("\"/api/v1/health\"").contains("\"/api/v1/appointments\"").contains("\"/api/v1/doctors\"")
                .as("wewnętrzne API nie trafia do kontraktu").doesNotContain("/internal/");

        Path target = repoRoot().resolve("api/services/scheduling-service.openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, json, StandardCharsets.UTF_8);

        aggregate();
    }

    /**
     * Składa api/openapi.json (kontrakt frontendu, klient Orval) z kontraktów trzech usług. scheduling-service jest
     * ostatni w reaktorze Mavena, więc pliki iam i catalog są już świeże; przy budowie samej tej usługi używa
     * zatwierdzonych. Ten sam schemat o tej samej nazwie w dwóch usługach musi być identyczny.
     */
    private static void aggregate() throws Exception {
        // Scalanie na drzewie JSON (Jackson 2 jak w springdoc): liczby, kolejność pól i format zostają takie, jak je
        // wygenerowały usługi; sortujemy tylko scalone mapy (ścieżki i schematy), jak writer-with-order-by-keys.
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
                .setNodeFactory(com.fasterxml.jackson.databind.node.JsonNodeFactory.withExactBigDecimals(true));
        com.fasterxml.jackson.databind.node.ObjectNode merged = null;
        for (String service : new String[] {"iam-service", "catalog-service", "scheduling-service"}) {
            Path file = repoRoot().resolve("api/services/" + service + ".openapi.json");
            var spec = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(Files.readString(file));
            if (merged == null) {
                merged = spec;
                continue;
            }
            var paths = (com.fasterxml.jackson.databind.node.ObjectNode) merged.get("paths");
            for (var path : spec.get("paths").properties()) {
                if (path.getKey().startsWith("/api/v1/health")) {
                    continue; // każda usługa ma własny health; w kontrakcie wystarczy jeden (krawędź kieruje do iam)
                }
                assertThat(paths.has(path.getKey())).as("ścieżka %s w dwóch usługach", path.getKey()).isFalse();
                paths.set(path.getKey(), path.getValue());
            }
            var schemas = (com.fasterxml.jackson.databind.node.ObjectNode) merged.get("components").get("schemas");
            if (spec.has("components") && spec.get("components").has("schemas")) {
                for (var schema : spec.get("components").get("schemas").properties()) {
                    if (schemas.has(schema.getKey())) {
                        assertThat(schemas.get(schema.getKey())).as("schemat %s różni się między usługami",
                                schema.getKey()).isEqualTo(schema.getValue());
                    }
                    schemas.set(schema.getKey(), schema.getValue());
                }
            }
        }
        sortFields((com.fasterxml.jackson.databind.node.ObjectNode) merged.get("paths"));
        sortFields((com.fasterxml.jackson.databind.node.ObjectNode) merged.get("components").get("schemas"));
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(merged).replace("\r\n", "\n") + "\n";
        Files.writeString(repoRoot().resolve("api/openapi.json"), json, StandardCharsets.UTF_8);
    }

    private static void sortFields(com.fasterxml.jackson.databind.node.ObjectNode node) {
        var sorted = new java.util.TreeMap<String, com.fasterxml.jackson.databind.JsonNode>();
        node.properties().forEach(e -> sorted.put(e.getKey(), e.getValue()));
        node.removeAll();
        node.setAll(sorted);
    }

    static Path repoRoot() {
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
