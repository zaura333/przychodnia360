package pl.przychodnia360.testsupport.contract;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Wyprowadza kontrakt OpenAPI każdej usługi logicznej z kontraktu zagregowanego (api/openapi.json) według tabeli
 * routingu: ścieżki usługi (i ścieżki {@code every}) oraz domknięcie przechodnie wskazywanych przez nie
 * {@code $ref} w components. Dziś źródłem jest monolit; po wycięciu usługi jej plik staje się źródłem prawdy,
 * a agregat składa się z plików usług.
 */
public final class OpenApiSplitter {

    private static final Pattern REF = Pattern.compile("#/components/([A-Za-z]+)/([^\"]+)");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private OpenApiSplitter() {
    }

    public static JsonNode parse(String json) {
        return MAPPER.readTree(json);
    }

    /** Wynik deterministyczny: kolejność z agregatu, wcięcie i zakończenie linii jak w pozostałych kontraktach. */
    public static String toJson(JsonNode node) {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node).replace("\r\n", "\n") + "\n";
    }

    public static Map<String, ObjectNode> split(JsonNode aggregate, RoutingTable routing) {
        Map<String, ObjectNode> result = new LinkedHashMap<>();
        for (String service : routing.services()) {
            result.put(service, forService(aggregate, routing, service));
        }
        return result;
    }

    private static ObjectNode forService(JsonNode aggregate, RoutingTable routing, String service) {
        ObjectNode out = MAPPER.createObjectNode();
        for (Map.Entry<String, JsonNode> field : aggregate.properties()) {
            switch (field.getKey()) {
                case "paths", "components" -> { /* składane poniżej */ }
                case "info" -> {
                    ObjectNode info = ((ObjectNode) field.getValue()).deepCopy();
                    info.put("title", info.path("title").asString("API") + " (" + service + ")");
                    out.set("info", info);
                }
                default -> out.set(field.getKey(), field.getValue().deepCopy());
            }
        }
        ObjectNode paths = MAPPER.createObjectNode();
        for (Map.Entry<String, JsonNode> path : aggregate.path("paths").properties()) {
            String owner = routing.serviceFor(path.getKey());
            if (owner.equals(service) || owner.equals(RoutingTable.EVERY)) {
                paths.set(path.getKey(), path.getValue().deepCopy());
            }
        }
        out.set("paths", paths);

        JsonNode components = aggregate.path("components");
        if (!components.isMissingNode()) {
            ObjectNode comps = MAPPER.createObjectNode();
            Set<String> wanted = closure(paths, components);
            for (Map.Entry<String, JsonNode> kind : components.properties()) {
                if (kind.getKey().equals("securitySchemes")) {
                    comps.set(kind.getKey(), kind.getValue().deepCopy());
                    continue;
                }
                ObjectNode selected = MAPPER.createObjectNode();
                for (Map.Entry<String, JsonNode> item : kind.getValue().properties()) {
                    if (wanted.contains(kind.getKey() + "/" + item.getKey())) {
                        selected.set(item.getKey(), item.getValue().deepCopy());
                    }
                }
                if (!selected.isEmpty()) {
                    comps.set(kind.getKey(), selected);
                }
            }
            if (!comps.isEmpty()) {
                out.set("components", comps);
            }
        }
        return out;
    }

    /** Nazwy "rodzaj/nazwa" komponentów osiągalnych przez $ref ze ścieżek (domknięcie przechodnie). */
    private static Set<String> closure(JsonNode paths, JsonNode components) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<JsonNode> queue = new ArrayDeque<>();
        queue.add(paths);
        while (!queue.isEmpty()) {
            JsonNode node = queue.poll();
            if (node.isObject()) {
                for (Map.Entry<String, JsonNode> field : node.properties()) {
                    if (field.getKey().equals("$ref") && field.getValue().isString()) {
                        Matcher m = REF.matcher(field.getValue().asString());
                        if (m.matches()) {
                            String key = m.group(1) + "/" + m.group(2);
                            if (seen.add(key)) {
                                JsonNode target = components.path(m.group(1)).path(m.group(2));
                                if (!target.isMissingNode()) {
                                    queue.add(target);
                                }
                            }
                        }
                    } else {
                        queue.add(field.getValue());
                    }
                }
            } else if (node.isArray()) {
                ArrayNode array = (ArrayNode) node;
                array.forEach(queue::add);
            }
        }
        return seen;
    }
}
