package pl.przychodnia360.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Scalanie eksportów usług w agregat i filtrowanie ścieżek wewnętrznych z domknięciem komponentów. */
class OpenApiAggregateTest {

    private static JsonNode spec(String paths, String schemas) {
        return OpenApiSplitter.parse("{\"openapi\":\"3.1.0\",\"info\":{\"title\":\"T\"},\"paths\":{" + paths
                + "},\"components\":{\"schemas\":{" + schemas + "}}}");
    }

    private static final String GET_A = "\"/a\":{\"get\":{\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":"
            + "{\"$ref\":\"#/components/schemas/A\"}}}}}}}";
    private static final String GET_B = "\"/b\":{\"get\":{\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":"
            + "{\"$ref\":\"#/components/schemas/B\"}}}}}}}";
    private static final String HEALTH = "\"/health\":{\"get\":{\"operationId\":\"h\"}}";

    @Test
    void mergesDisjointPathsAndComponentsInAlphabeticalOrder() {
        JsonNode merged = OpenApiAggregate.merge(List.of(spec(GET_B + "," + HEALTH, "\"B\":{\"type\":\"object\"}"),
                spec(GET_A, "\"A\":{\"type\":\"string\"}")));

        List<String> paths = merged.path("paths").propertyNames().stream().toList();
        assertThat(paths).containsExactly("/a", "/b", "/health");
        assertThat(merged.path("components").path("schemas").propertyNames().stream().toList()).containsExactly("A", "B");
    }

    @Test
    void identicalDuplicatesAreMergedAndConflictingOnesFail() {
        JsonNode one = spec(HEALTH, "\"A\":{\"type\":\"string\"}");
        JsonNode same = spec(HEALTH, "\"A\":{\"type\":\"string\"}");
        assertThat(OpenApiAggregate.merge(List.of(one, same)).path("paths").size()).isEqualTo(1);

        JsonNode otherPath = spec("\"/health\":{\"get\":{\"operationId\":\"inny\"}}", "\"A\":{\"type\":\"string\"}");
        assertThatThrownBy(() -> OpenApiAggregate.merge(List.of(one, otherPath))).hasMessageContaining("ścieżka '/health'");
        JsonNode otherSchema = spec(HEALTH, "\"A\":{\"type\":\"integer\"}");
        assertThatThrownBy(() -> OpenApiAggregate.merge(List.of(one, otherSchema))).hasMessageContaining("schemas");
    }

    @Test
    void filteringDropsInternalPathsAndTheirOnlySchemas() {
        JsonNode full = spec(GET_A + "," + GET_B.replace("\"/b\"", "\"/api/v1/internal/b\""),
                "\"A\":{\"type\":\"string\"},\"B\":{\"type\":\"object\"}");

        JsonNode filtered = OpenApiSplitter.filterPaths(full, p -> !p.startsWith("/api/v1/internal/"));

        assertThat(filtered.path("paths").propertyNames().stream().toList()).containsExactly("/a");
        assertThat(filtered.path("components").path("schemas").propertyNames().stream().toList()).containsExactly("A");
    }
}
