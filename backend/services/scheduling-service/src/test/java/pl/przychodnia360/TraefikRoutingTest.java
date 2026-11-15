package pl.przychodnia360;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Krawędź (docker/traefik/dynamic.yml) kieruje każdą ścieżkę kontraktu api/openapi.json do dokładnie jednej usługi,
 * i to tej samej, którą wskazuje routing-table.csv (ADR-M06, ADR-M07). Usługi logiczne „patient” i „every” żyją na
 * razie w iam-service.
 */
class TraefikRoutingTest {

    private static final Pattern ROUTER = Pattern.compile("(?ms)^    (\\w+):\\n.*?rule: >-\\n(.*?)(?=^    \\w+:\\n|^  services:)");
    private static final Pattern MATCHER = Pattern.compile("(PathPrefix|Path)\\(`([^`]+)`\\)");
    private static final Map<String, String> DEPLOYED_IN = Map.of(
            "iam", "iam", "patient", "iam", "every", "iam", "catalog", "catalog", "scheduling", "scheduling");

    private record EdgeRule(String router, boolean prefix, String path) {
        boolean matches(String apiPath) {
            return prefix ? apiPath.equals(path) || apiPath.startsWith(path + "/") : apiPath.equals(path);
        }
    }

    @Test
    void edgeRoutesEveryContractPathToTheServiceFromTheRoutingTable() throws IOException {
        Path root = OpenApiExportTest.repoRoot();
        String yaml = Files.readString(root.resolve("docker/traefik/dynamic.yml"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        List<EdgeRule> matchers = new ArrayList<>();
        Matcher routers = ROUTER.matcher(yaml);
        while (routers.find()) {
            Matcher m = MATCHER.matcher(routers.group(2));
            while (m.find()) {
                matchers.add(new EdgeRule(routers.group(1), m.group(1).equals("PathPrefix"), m.group(2)));
            }
        }
        assertThat(matchers).extracting(EdgeRule::router).contains("iam", "catalog", "scheduling");

        Map<String, String> table = new LinkedHashMap<>();
        try (var in = getClass().getResourceAsStream("/routing-table.csv")) {
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
            for (int i = 1; i < lines.length; i++) {
                if (!lines[i].isBlank()) {
                    String[] cells = lines[i].split(",");
                    table.put(cells[0], cells[1]);
                }
            }
        }

        JsonNode spec = JsonMapper.builder().build().readTree(Files.readString(root.resolve("api/openapi.json")));
        List<String> paths = new ArrayList<>();
        spec.get("paths").propertyNames().forEach(paths::add);
        assertThat(paths).isNotEmpty();
        for (String apiPath : paths) {
            List<String> edge = matchers.stream().filter(m -> m.matches(apiPath)).map(EdgeRule::router).distinct()
                    .toList();
            assertThat(edge).as("routery krawędzi dla %s", apiPath).hasSize(1);
            String logical = table.entrySet().stream()
                    .filter(e -> PathPatternParser.defaultInstance.parse(e.getKey())
                            .matches(PathContainer.parsePath(apiPath)))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
            assertThat(edge.getFirst()).as("usługa dla %s", apiPath).isEqualTo(DEPLOYED_IN.get(logical));
        }
    }
}
