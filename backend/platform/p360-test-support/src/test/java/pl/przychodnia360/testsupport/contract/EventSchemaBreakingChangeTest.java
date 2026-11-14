package pl.przychodnia360.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Zmiany łamiące względem gałęzi bazowej (CI): katalog ze schematami bazy podaje właściwość
 * {@code p360.events.base-dir} (lub zmienna P360_EVENTS_BASE_DIR). Bez niej test jest pomijany (lokalnie).
 * Usunięty typ zdarzenia i usunięta koperta też są zmianami łamiącymi.
 */
class EventSchemaBreakingChangeTest {

    private static Path baseDir() {
        String value = System.getProperty("p360.events.base-dir", System.getenv("P360_EVENTS_BASE_DIR"));
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    @Test
    void noBreakingChangesComparedToTheBaseBranch() {
        Path base = baseDir();
        assumeTrue(base != null && Files.isDirectory(base), "brak katalogu bazy (p360.events.base-dir)");

        List<String> problems = new ArrayList<>();
        Path current = EventSchemas.eventsDir();
        Path baseEnvelope = base.resolve("envelope.schema.json");
        if (Files.exists(baseEnvelope)) {
            EventSchemaCompatibility.breakingChanges(EventSchemas.read(baseEnvelope),
                    EventSchemas.read(current.resolve("envelope.schema.json")))
                    .forEach(p -> problems.add("envelope: " + p));
        }
        Map<String, Path> currentFiles = EventSchemas.eventSchemaFiles(current);
        for (Map.Entry<String, Path> baseEntry : EventSchemas.eventSchemaFiles(base).entrySet()) {
            Path now = currentFiles.get(baseEntry.getKey());
            if (now == null) {
                problems.add(baseEntry.getKey() + ": usunięto typ zdarzenia");
                continue;
            }
            JsonNode before = EventSchemas.read(baseEntry.getValue());
            JsonNode after = EventSchemas.read(now);
            EventSchemaCompatibility.breakingChanges(before, after)
                    .forEach(p -> problems.add(baseEntry.getKey() + ": " + p));
        }
        assertThat(problems).as("zmiany łamiące kontrakty zdarzeń (użyj nowego typu zdarzenia zamiast edycji w miejscu)")
                .isEmpty();
    }
}
