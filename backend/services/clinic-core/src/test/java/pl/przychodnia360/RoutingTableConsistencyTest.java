package pl.przychodnia360;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fala 0R, 0R.7 (ADR-M06): każda ścieżka z api/openapi.json ma dokładnie jedną regułę routingu (usługę docelową),
 * a żadna reguła nie jest martwa. Tabela docker/edge/routing-table.csv jest źródłem reguł Traefika (EdgeRoutesTest), używanego lokalnie i w AWS.
 * Prefiksy /admin, /me i /reception łączą kilka usług, więc reguły są na poziomie podścieżek.
 */
class RoutingTableConsistencyTest {

    private record Rule(String pattern, String service, PathPattern parsed) {
    }

    @Test
    void everyApiPathHasExactlyOneRoutingRule() throws IOException {
        List<Rule> rules = new ArrayList<>();
        String[] lines = Files.readString(repoRoot().resolve("docker/edge/routing-table.csv"),
                StandardCharsets.UTF_8).split("\\R");
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                String[] cells = lines[i].split(",");
                rules.add(new Rule(cells[0], cells[1], PathPatternParser.defaultInstance.parse(cells[0])));
            }
        }
        JsonNode spec = JsonMapper.builder().build().readTree(Files.readString(repoRoot().resolve("api/openapi.json")));
        List<String> paths = new ArrayList<>();
        spec.get("paths").propertyNames().forEach(paths::add);
        assertThat(paths).isNotEmpty();

        for (String path : paths) {
            List<String> matching = rules.stream()
                    .filter(r -> r.parsed().matches(PathContainer.parsePath(path)))
                    .map(Rule::pattern).toList();
            assertThat(matching).as("reguły dla %s", path).hasSize(1);
        }
        for (Rule rule : rules) {
            assertThat(paths).as("martwa reguła %s", rule.pattern())
                    .anyMatch(p -> rule.parsed().matches(PathContainer.parsePath(p)));
        }
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
