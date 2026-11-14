package pl.przychodnia360.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Testy kontraktowe konsument ↔ dostawca: każdy plik {@code api/events/consumers/<konsument>/<Zdarzenie>.json}
 * deklaruje pola i typy, których konsument używa. Schemat dostawcy musi je definiować jako wymagane, z tym samym
 * typem i formatem. Usunięcie lub zmiana takiego pola w schemacie zdarzenia zatrzymuje CI (wcześniej niż u konsumenta).
 */
class EventConsumerContractsTest {

    @Test
    void everyConsumerExpectationIsSatisfiedByTheProviderSchema() throws IOException {
        Path consumers = EventSchemas.eventsDir().resolve("consumers");
        assertThat(Files.isDirectory(consumers)).as("api/events/consumers").isTrue();
        Map<String, Path> providers = EventSchemas.eventSchemaFiles(EventSchemas.eventsDir());

        List<String> problems = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.walk(consumers)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonNode contract = EventSchemas.read(file);
                String consumer = contract.path("consumer").asText();
                String event = contract.path("event").asText();
                String where = consumer + " <- " + event;
                checked++;

                assertThat(file.getParent().getFileName().toString()).as("%s: katalog = konsument", where)
                        .isEqualTo(consumer);
                assertThat(file.getFileName().toString()).as("%s: plik = zdarzenie", where).isEqualTo(event + ".json");
                Path providerFile = providers.get(event);
                if (providerFile == null) {
                    problems.add(where + ": dostawca nie ma schematu zdarzenia");
                    continue;
                }
                JsonNode provider = EventSchemas.read(providerFile);
                List<String> required = new ArrayList<>();
                provider.path("required").forEach(n -> required.add(n.asText()));
                contract.path("requires").properties().forEach(field -> {
                    JsonNode offered = provider.path("properties").path(field.getKey());
                    if (offered.isMissingNode()) {
                        problems.add(where + ": brak pola '" + field.getKey() + "'");
                        return;
                    }
                    if (!offered.path("type").equals(field.getValue().path("type"))) {
                        problems.add(where + ": pole '" + field.getKey() + "' ma typ " + offered.path("type")
                                + ", konsument oczekuje " + field.getValue().path("type"));
                    }
                    if (!offered.path("format").equals(field.getValue().path("format"))) {
                        problems.add(where + ": pole '" + field.getKey() + "' ma format " + offered.path("format")
                                + ", konsument oczekuje " + field.getValue().path("format"));
                    }
                    if (!required.contains(field.getKey())) {
                        problems.add(where + ": pole '" + field.getKey() + "' nie jest wymagane u dostawcy");
                    }
                });
            }
        }
        assertThat(checked).as("co najmniej jedno oczekiwanie konsumenta").isPositive();
        assertThat(problems).isEmpty();
    }
}
