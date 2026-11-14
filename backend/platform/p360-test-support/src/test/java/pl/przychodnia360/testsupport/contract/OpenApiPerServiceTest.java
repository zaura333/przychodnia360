package pl.przychodnia360.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Kontrakt OpenAPI per usługa logiczna (api/services/<usługa>.openapi.json) wyprowadzony z agregatu
 * (api/openapi.json) według tabeli routingu. Zatwierdzone pliki muszą być aktualne; po zmianie agregatu lub tabeli
 * uruchom test z {@code -Dp360.openapi.update=true}. Każda ścieżka należy do dokładnie jednej usługi
 * (poza {@code every}), a suma ścieżek usług daje agregat.
 */
class OpenApiPerServiceTest {

    private static final String UPDATE_PROPERTY = "p360.openapi.update";

    private final Path root = EventSchemas.repoRoot();
    private final RoutingTable routing = RoutingTable.load();
    private final JsonNode aggregate = OpenApiSplitter.parse(read(root.resolve("api/openapi.json")));
    private final Map<String, ObjectNode> split = OpenApiSplitter.split(aggregate, routing);

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void committedPerServiceContractsMatchTheAggregate() throws IOException {
        Path dir = root.resolve("api/services");
        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Files.createDirectories(dir);
            for (Map.Entry<String, ObjectNode> entry : split.entrySet()) {
                Files.writeString(dir.resolve(entry.getKey() + ".openapi.json"), OpenApiSplitter.toJson(entry.getValue()),
                        StandardCharsets.UTF_8);
            }
        }
        for (Map.Entry<String, ObjectNode> entry : split.entrySet()) {
            Path file = dir.resolve(entry.getKey() + ".openapi.json");
            assertThat(Files.exists(file)).as("%s istnieje (uruchom z -D%s=true)", file.getFileName(), UPDATE_PROPERTY)
                    .isTrue();
            assertThat(read(file)).as("%s jest nieaktualny: uruchom z -D%s=true", file.getFileName(), UPDATE_PROPERTY)
                    .isEqualTo(OpenApiSplitter.toJson(entry.getValue()));
        }
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".openapi.json")).toList())
                    .as("nie ma plików usług spoza tabeli routingu")
                    .containsExactlyInAnyOrderElementsOf(split.keySet().stream().map(s -> s + ".openapi.json").toList());
        }
    }

    @Test
    void everyPathBelongsToExactlyOneServiceAndTheUnionIsTheAggregate() {
        Set<String> union = new HashSet<>();
        List<String> shared = new ArrayList<>();
        aggregate.path("paths").propertyNames().forEach(path -> {
            String owner = routing.serviceFor(path);
            if (owner.equals(RoutingTable.EVERY)) {
                shared.add(path);
            }
        });
        split.forEach((service, spec) -> spec.path("paths").propertyNames().forEach(path -> {
            union.add(path);
            if (!shared.contains(path)) {
                assertThat(routing.serviceFor(path)).as("%s: ścieżka %s", service, path).isEqualTo(service);
            }
        }));

        Set<String> all = new HashSet<>();
        aggregate.path("paths").propertyNames().forEach(all::add);
        assertThat(union).isEqualTo(all);
    }
}
