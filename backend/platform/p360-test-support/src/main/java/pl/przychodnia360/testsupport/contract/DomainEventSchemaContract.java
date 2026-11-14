package pl.przychodnia360.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import pl.przychodnia360.common.events.EventEnvelope;
import pl.przychodnia360.shared.DomainEvent;
import pl.przychodnia360.testsupport.PostgresIntegrationTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kontrakt zdarzeń usługi (zadanie 1.8): każdy rekord {@link DomainEvent} ma schemat w api/events, żaden schemat
 * usługi nie jest osierocony, wersja w schemacie zgadza się z {@code schemaVersion()}, a zdarzenie serializowane
 * aplikacyjnym {@link JsonMapper} (samo i w kopercie) jest zgodne ze schematem. Przykładowe zdarzenia powstają
 * z komponentów rekordu, więc nowe zdarzenie nie wymaga ręcznej fikstury.
 */
public abstract class DomainEventSchemaContract extends PostgresIntegrationTest {

    @Autowired
    protected JsonMapper mapper;

    /** Pakiet do przeszukania w poszukiwaniu zdarzeń usługi. */
    protected String eventsBasePackage() {
        return "pl.przychodnia360";
    }

    /** Usługi logiczne (x-producer), których zdarzenia publikuje ten proces; schematy innych usług są pomijane. */
    protected abstract Set<String> producedServices();

    @SuppressWarnings("unchecked")
    private Map<String, Class<? extends DomainEvent>> eventClasses() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(DomainEvent.class));
        Map<String, Class<? extends DomainEvent>> classes = new TreeMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(eventsBasePackage())) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (type.isRecord() && Modifier.isPublic(type.getModifiers())) {
                classes.put(type.getSimpleName(), (Class<? extends DomainEvent>) type);
            }
        }
        return classes;
    }

    private DomainEvent sample(Class<? extends DomainEvent> type) throws ReflectiveOperationException {
        RecordComponent[] components = type.getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            Class<?> t = components[i].getType();
            types[i] = t;
            values[i] = t == UUID.class ? UUID.randomUUID()
                    : t == Instant.class ? Instant.now()
                    : t == LocalDate.class ? LocalDate.now()
                    : t == String.class ? "x"
                    : t == BigDecimal.class ? BigDecimal.ONE
                    : t == int.class || t == Integer.class ? Integer.valueOf(1)
                    : t == long.class || t == Long.class ? Long.valueOf(1)
                    : t == boolean.class || t == Boolean.class ? Boolean.TRUE
                    : null;
            assertThat(values[i]).as("%s.%s: nieobsługiwany typ %s w zdarzeniu (dodaj do sample)", type.getSimpleName(),
                    components[i].getName(), t.getSimpleName()).isNotNull();
        }
        return type.getDeclaredConstructor(types).newInstance(values);
    }

    @Test
    void everyDomainEventHasASchemaAndNoSchemaOfThisServiceIsOrphaned() throws Exception {
        Map<String, Class<? extends DomainEvent>> classes = eventClasses();
        Map<String, Path> schemas = EventSchemas.eventSchemaFiles(EventSchemas.eventsDir());

        assertThat(classes).as("zdarzenia domenowe usługi").isNotEmpty();
        assertThat(schemas.keySet()).as("zdarzenie bez schematu w api/events (dodaj <Typ>.schema.json)")
                .containsAll(classes.keySet());
        List<String> orphans = new ArrayList<>();
        schemas.forEach((name, file) -> {
            String producer = EventSchemas.read(file).path("x-producer").asText();
            if (producedServices().contains(producer) && !classes.containsKey(name)) {
                orphans.add(name);
            }
        });
        assertThat(orphans).as("schematy bez rekordu zdarzenia w tej usłudze").isEmpty();
    }

    @Test
    void serializedEventsAndEnvelopesValidateAgainstTheirSchemas() throws Exception {
        JsonNode envelopeSchema = EventSchemas.read(EventSchemas.eventsDir().resolve("envelope.schema.json"));
        Map<String, Path> schemas = EventSchemas.eventSchemaFiles(EventSchemas.eventsDir());

        for (Map.Entry<String, Class<? extends DomainEvent>> entry : eventClasses().entrySet()) {
            String name = entry.getKey();
            DomainEvent event = sample(entry.getValue());
            JsonNode schema = EventSchemas.read(schemas.get(name));

            assertThat(schema.path("x-schemaVersion").asInt()).as("%s: x-schemaVersion = schemaVersion()", name)
                    .isEqualTo(event.schemaVersion());

            String payloadJson = mapper.writeValueAsString(event);
            assertThat(EventSchemas.validate(schema, EventSchemas.parse(payloadJson)))
                    .as("%s: ładunek niezgodny ze schematem (%s)", name, payloadJson).isEmpty();

            for (String traceparent : new String[] {"00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", null}) {
                var envelope = new EventEnvelope(UUID.randomUUID(), event.type(), event.schemaVersion(),
                        event.tenantId(), event.aggregateId(), event.occurredAt(), traceparent,
                        mapper.readTree(payloadJson));
                String envelopeJson = mapper.writeValueAsString(envelope);
                assertThat(EventSchemas.validate(envelopeSchema, EventSchemas.parse(envelopeJson)))
                        .as("%s: koperta niezgodna ze schematem (%s)", name, envelopeJson).isEmpty();
            }
        }
    }
}
