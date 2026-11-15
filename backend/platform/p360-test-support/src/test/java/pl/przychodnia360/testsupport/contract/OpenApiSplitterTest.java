package pl.przychodnia360.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

class OpenApiSplitterTest {

    private static final String ROUTING = """
            pattern,service
            /api/v1/a/**,alpha
            /api/v1/b,beta
            /api/v1/health,every
            """;

    private static final String SPEC = """
            {"openapi":"3.1.0","info":{"title":"API","version":"1"},
             "paths":{
               "/api/v1/a/items":{"get":{"responses":{"200":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Item"}}}}}}},
               "/api/v1/b":{"get":{"responses":{"200":{"content":{"application/json":{"schema":{"$ref":"#/components/schemas/Other"}}}}}}},
               "/api/v1/health":{"get":{"responses":{"200":{"description":"ok"}}}}},
             "components":{"schemas":{
               "Item":{"type":"object","properties":{"part":{"$ref":"#/components/schemas/Part"}}},
               "Part":{"type":"object"},
               "Other":{"type":"object"},
               "Unused":{"type":"object"}},
              "securitySchemes":{"bearer":{"type":"http","scheme":"bearer"}}}}""";

    private final Map<String, ObjectNode> split = OpenApiSplitter.split(OpenApiSplitter.parse(SPEC),
            RoutingTable.parse(ROUTING));

    private static java.util.List<String> names(JsonNode node) {
        java.util.List<String> names = new java.util.ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    @Test
    void eachServiceGetsItsPathsPlusTheSharedOnes() {
        assertThat(split.keySet()).containsExactly("alpha", "beta");
        assertThat(names(split.get("alpha").get("paths"))).containsExactly("/api/v1/a/items", "/api/v1/health");
        assertThat(names(split.get("beta").get("paths"))).containsExactly("/api/v1/b", "/api/v1/health");
    }

    @Test
    void referencedSchemasAreIncludedTransitivelyAndUnusedOnesDropped() {
        assertThat(names(split.get("alpha").get("components").get("schemas"))).containsExactly("Item", "Part");
        assertThat(names(split.get("beta").get("components").get("schemas"))).containsExactly("Other");
        assertThat(split.get("alpha").get("components").has("securitySchemes")).isTrue();
    }

    @Test
    void titleNamesTheService() {
        assertThat(split.get("alpha").get("info").get("title").asString()).isEqualTo("API (alpha)");
    }

    @Test
    void aPathWithoutExactlyOneRuleFails() {
        String unrouted = SPEC.replace("\"/api/v1/health\":", "\"/api/v1/nowa\":");
        assertThatThrownBy(() -> OpenApiSplitter.split(OpenApiSplitter.parse(unrouted), RoutingTable.parse(ROUTING)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("/api/v1/nowa");
    }
}
