package pl.przychodnia360.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Struktura schematów w api/events: każdy plik jest poprawnym, ścisłym schematem z właścicielem i wersją. */
class EventSchemaFilesTest {

    @Test
    void thereAreEventSchemasAndAnEnvelope() {
        assertThat(Files.exists(EventSchemas.eventsDir().resolve("envelope.schema.json"))).isTrue();
        assertThat(EventSchemas.eventSchemaFiles(EventSchemas.eventsDir())).isNotEmpty();
    }

    @Test
    void everyEventSchemaIsStrictVersionedAndOwnedByAKnownService() {
        Set<String> services = new HashSet<>(RoutingTable.load().services());
        for (Map.Entry<String, Path> entry : EventSchemas.eventSchemaFiles(EventSchemas.eventsDir()).entrySet()) {
            JsonNode schema = EventSchemas.read(entry.getValue());
            String name = entry.getKey();

            assertThat(schema.path("$id").asText()).as("%s: $id", name).isEqualTo("https://schemas.przychodnia360.example/events/" + name + ".schema.json");
            assertThat(schema.path("type").asText()).as("%s: typ", name).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean(true))
                    .as("%s: additionalProperties=false (ścisły kontrakt dostawcy)", name).isFalse();
            assertThat(schema.path("x-schemaVersion").asInt(0)).as("%s: x-schemaVersion", name).isGreaterThanOrEqualTo(1);
            assertThat(services).as("%s: x-producer musi być usługą z routing-table.csv", name)
                    .contains(schema.path("x-producer").asText());

            Set<String> properties = new HashSet<>();
            schema.path("properties").fieldNames().forEachRemaining(properties::add);
            Set<String> required = new HashSet<>();
            schema.path("required").forEach(n -> required.add(n.asText()));
            assertThat(properties).as("%s: koperta zdarzenia", name).contains("tenantId", "aggregateId", "occurredAt");
            assertThat(required).as("%s: tenantId jest obowiązkowy (izolacja placówek)", name).contains("tenantId");
            assertThat(properties).as("%s: wymagane pola muszą być zdefiniowane", name).containsAll(required);
        }
    }

    @Test
    void noEventSchemaDeclaresPersonalData() {
        java.util.regex.Pattern pii = java.util.regex.Pattern.compile(
                "(?i).*(first|last)name|.*pesel.*|.*email.*|.*phone.*|.*birth.*|.*address.*|.*password.*");
        for (Map.Entry<String, Path> entry : EventSchemas.eventSchemaFiles(EventSchemas.eventsDir()).entrySet()) {
            entry.getValue();
            EventSchemas.read(entry.getValue()).path("properties").fieldNames()
                    .forEachRemaining(f -> assertThat(pii.matcher(f).matches())
                            .as("%s: pole '%s' wygląda na dane osobowe (ADR-M10)", entry.getKey(), f).isFalse());
        }
    }
}
