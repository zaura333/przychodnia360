package pl.przychodnia360.testsupport.contract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Agregat kontraktu REST (api/openapi.json, źródło klienta frontendu) składany z eksportów usług
 * ({@code api/exports/<usługa>.json}); każda usługa eksportuje swoje ścieżki publiczne (bez {@code /api/v1/internal/**}).
 * Ścieżki i komponenty o tej samej nazwie w kilku eksportach muszą być identyczne (np. sondy zdrowia, wspólne schematy
 * błędów), inaczej scalanie kończy się błędem, a nie cichym wyborem jednej wersji.
 *
 * <p>Każda usługa po własnym eksporcie przelicza agregat z (swojego świeżego i zatwierdzonych cudzych) eksportów, więc
 * zadanie CI dowolnej usługi wykrywa rozjazd {@code git diff api/}. Kolejność modułów w reaktorze ma clinic-core na
 * końcu, dzięki czemu pełny {@code verify} kończy się agregatem ze wszystkich świeżych eksportów.
 */
public final class OpenApiAggregate {

    public static final String EXPORT_DIR = "api/exports";
    public static final String AGGREGATE = "api/openapi.json";
    private static final String INTERNAL_PREFIX = "/api/v1/internal/";

    private OpenApiAggregate() {
    }

    /** Zapisuje eksport usługi (bez ścieżek wewnętrznych) i przelicza agregat. */
    public static void exportAndAggregate(String service, String springdocJson) {
        Path root = EventSchemas.repoRoot();
        ObjectNode export = OpenApiSplitter.filterPaths(OpenApiSplitter.parse(springdocJson),
                path -> !path.startsWith(INTERNAL_PREFIX));
        write(root.resolve(EXPORT_DIR).resolve(service + ".json"), OpenApiSplitter.toJson(export));
        write(root.resolve(AGGREGATE), OpenApiSplitter.toJson(merge(readExports(root))));
    }

    /** Eksporty usług (kolejność alfabetyczna po nazwie pliku; pierwszy dostarcza nagłówek kontraktu). */
    public static List<JsonNode> readExports(Path root) {
        List<JsonNode> specs = new ArrayList<>();
        try (var files = Files.list(root.resolve(EXPORT_DIR))) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(p -> {
                try {
                    specs.add(OpenApiSplitter.parse(Files.readString(p, StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return specs;
    }

    /** Scala eksporty: ścieżki i komponenty w kolejności alfabetycznej, nagłówek z pierwszego eksportu. */
    public static ObjectNode merge(List<JsonNode> specs) {
        if (specs.isEmpty()) {
            throw new IllegalStateException("Brak eksportów usług w " + EXPORT_DIR);
        }
        ObjectNode out = ((ObjectNode) specs.getFirst()).deepCopy();

        Map<String, JsonNode> paths = new TreeMap<>();
        Map<String, Map<String, JsonNode>> components = new TreeMap<>();
        for (JsonNode spec : specs) {
            collect(paths, spec.path("paths"), "ścieżka");
            for (Map.Entry<String, JsonNode> kind : spec.path("components").properties()) {
                collect(components.computeIfAbsent(kind.getKey(), k -> new TreeMap<>()), kind.getValue(),
                        "komponent " + kind.getKey());
            }
        }
        ObjectNode mergedPaths = out.objectNode();
        paths.forEach(mergedPaths::set);
        out.set("paths", mergedPaths);
        if (!components.isEmpty()) {
            ObjectNode mergedComponents = out.objectNode();
            components.forEach((kind, items) -> {
                ObjectNode node = out.objectNode();
                items.forEach(node::set);
                mergedComponents.set(kind, node);
            });
            out.set("components", mergedComponents);
        }
        return out;
    }

    private static void collect(Map<String, JsonNode> target, JsonNode source, String what) {
        for (Map.Entry<String, JsonNode> item : source.properties()) {
            JsonNode existing = target.putIfAbsent(item.getKey(), item.getValue());
            if (existing != null && !existing.equals(item.getValue())) {
                throw new IllegalStateException("Konflikt przy scalaniu OpenAPI: " + what + " '" + item.getKey()
                        + "' różni się między eksportami usług");
            }
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
