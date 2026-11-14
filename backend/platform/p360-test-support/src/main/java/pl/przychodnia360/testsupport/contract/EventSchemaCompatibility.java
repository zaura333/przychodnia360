package pl.przychodnia360.testsupport.contract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wykrywanie zmian łamiących w schemacie zdarzenia z punktu widzenia istniejących konsumentów. Zasady:
 * usunięcie pola, zmiana jego typu lub formatu, pole wymagane stające się opcjonalnym oraz obniżenie
 * {@code x-schemaVersion} są łamiące. Nowe pola (opcjonalne lub wymagane) są zgodne wstecz, bo konsument ma
 * ignorować nieznane pola. Sprawdzane są własności najwyższego poziomu ładunku; zagnieżdżone obiekty nie występują.
 */
public final class EventSchemaCompatibility {

    private EventSchemaCompatibility() {
    }

    /** Lista opisów zmian łamiących przy przejściu ze schematu {@code before} na {@code after} (pusta: zgodne). */
    public static List<String> breakingChanges(JsonNode before, JsonNode after) {
        List<String> problems = new ArrayList<>();
        JsonNode oldProps = before.path("properties");
        JsonNode newProps = after.path("properties");
        Set<String> oldRequired = names(before.path("required"));
        Set<String> newRequired = names(after.path("required"));

        Iterator<Map.Entry<String, JsonNode>> it = oldProps.properties().iterator();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> property = it.next();
            String name = property.getKey();
            JsonNode replacement = newProps.get(name);
            if (replacement == null) {
                problems.add("usunięto pole '" + name + "'");
                continue;
            }
            if (!property.getValue().path("type").equals(replacement.path("type"))) {
                problems.add("zmieniono typ pola '" + name + "': " + property.getValue().path("type") + " -> "
                        + replacement.path("type"));
            }
            if (!property.getValue().path("format").equals(replacement.path("format"))) {
                problems.add("zmieniono format pola '" + name + "': " + property.getValue().path("format") + " -> "
                        + replacement.path("format"));
            }
            if (oldRequired.contains(name) && !newRequired.contains(name)) {
                problems.add("pole '" + name + "' przestało być wymagane");
            }
        }
        int oldVersion = before.path("x-schemaVersion").asInt(1);
        int newVersion = after.path("x-schemaVersion").asInt(1);
        if (newVersion < oldVersion) {
            problems.add("obniżono x-schemaVersion: " + oldVersion + " -> " + newVersion);
        }
        return problems;
    }

    private static Set<String> names(JsonNode array) {
        Set<String> names = new HashSet<>();
        array.forEach(n -> names.add(n.asText()));
        return names;
    }
}
