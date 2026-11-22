package pl.przychodnia360.testsupport.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Dostęp do schematów zdarzeń (api/events) i ich walidacja (JSON Schema 2020-12, formaty jako asercje). */
public final class EventSchemas {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SchemaRegistryConfig CONFIG = SchemaRegistryConfig.builder()
            .formatAssertionsEnabled(true).build();
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(CONFIG));

    private EventSchemas() {
    }

    /** Korzeń repozytorium (katalog z docker/), niezależnie od katalogu roboczego Mavena lub IDE. */
    public static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("docker"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("Nie znaleziono korzenia repozytorium");
        }
        return dir;
    }

    public static Path eventsDir() {
        return repoRoot().resolve("api/events");
    }

    public static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static JsonNode read(Path file) {
        try {
            return MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Pliki schematów typów zdarzeń (bez koperty): nazwa typu -> plik. */
    public static java.util.Map<String, Path> eventSchemaFiles(Path dir) {
        java.util.Map<String, Path> files = new java.util.TreeMap<>();
        if (!Files.isDirectory(dir)) {
            return files;
        }
        try (Stream<Path> list = Files.list(dir)) {
            list.filter(p -> p.getFileName().toString().endsWith(".schema.json"))
                    .filter(p -> !p.getFileName().toString().equals("envelope.schema.json"))
                    .forEach(p -> files.put(p.getFileName().toString().replace(".schema.json", ""), p));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    /** Komunikaty błędów walidacji instancji względem schematu; pusta lista oznacza zgodność. */
    public static List<String> validate(JsonNode schema, JsonNode instance) {
        // walidator 3.x używa Jacksona 3 (tools.jackson), a kontrakty Jacksona 2: granica przez JSON jako tekst
        Schema compiled = REGISTRY.getSchema(schema.toString());
        List<String> errors = new ArrayList<>();
        compiled.validate(instance.toString(), InputFormat.JSON).forEach(e -> errors.add(e.getMessage()));
        return errors;
    }
}
