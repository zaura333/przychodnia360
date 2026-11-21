package pl.przychodnia360.testsupport.contract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Kontrakt wewnętrznego API usługi ({@code api/internal/<usługa>.json}, ścieżki {@code /api/v1/internal/**}) widziany
 * oczami konsumenta. Test adaptera HTTP (np. {@code RemotePatientDirectory}) podstawia atrapę serwera, która dla każdego
 * odebranego żądania woła {@link #verify}: żądanie musi odpowiadać operacji z kontraktu dostawcy (metoda, ścieżka,
 * parametry zapytania, pola i typy treści), a atrapa odpowiada przykładem wygenerowanym ze schematu odpowiedzi
 * dostawcy, więc adapter deserializuje dokładnie to, co dostawca deklaruje. {@link #responseProblems} sprawdza
 * dodatkowo, że rekord konsumenta nie czyta pól, których dostawca nie zwraca.
 *
 * <p>Plik kontraktu powstaje z kodu dostawcy ({@link OpenApiAggregate#exportAndAggregate}), więc zmiana endpointu
 * wewnętrznego bez zmiany adaptera (albo odwrotnie) kończy się czerwonym testem konsumenta.
 */
public final class InternalApiContract {

    /** Wynik sprawdzenia jednego żądania: dopasowana operacja, odpowiedź atrapy i wykryte rozbieżności. */
    public record Verdict(String operation, int status, String body, List<String> problems) {
    }

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final int MAX_DEPTH = 6;
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{[^}]+}");

    private final String provider;
    private final JsonNode spec;

    private InternalApiContract(String provider, JsonNode spec) {
        this.provider = provider;
        this.spec = spec;
    }

    /** Wczytuje kontrakt wewnętrzny dostawcy; brak pliku to błąd (usługa nie ma ścieżek wewnętrznych). */
    public static InternalApiContract of(String provider) {
        Path file = EventSchemas.repoRoot().resolve(OpenApiAggregate.INTERNAL_DIR).resolve(provider + ".json");
        try {
            return new InternalApiContract(provider, MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException("Brak kontraktu wewnętrznego " + file + " (uruchom OpenApiExportTest dostawcy)", e);
        }
    }

    /** Sprawdza żądanie adaptera względem kontraktu i zwraca odpowiedź, jaką dostawca zadeklarował dla operacji. */
    public Verdict verify(String method, String path, String rawQuery, String body) {
        List<String> problems = new ArrayList<>();
        String template = templateFor(path);
        JsonNode operation = template == null ? null : spec.path("paths").path(template).path(method.toLowerCase());
        if (operation == null || operation.isMissingNode()) {
            problems.add(provider + " nie ma operacji " + method + " " + path);
            return new Verdict(null, 404, "{}", problems);
        }
        String key = method + " " + template;

        checkQuery(operation, rawQuery, key, problems);

        JsonNode requestSchema = schemaOf(operation.path("requestBody").path("content"));
        boolean hasBody = body != null && !body.isBlank();
        if (requestSchema == null && hasBody) {
            problems.add(key + ": adapter wysyła treść, a kontrakt jej nie przewiduje");
        } else if (requestSchema != null) {
            if (!hasBody) {
                problems.add(key + ": kontrakt wymaga treści, adapter jej nie wysyła");
            } else {
                checkValue(requestSchema, MAPPER.readTree(body), key + " (treść)", problems, 0);
            }
        }

        String status = successStatus(operation);
        JsonNode responseSchema = schemaOf(operation.path("responses").path(status).path("content"));
        String sample = responseSchema == null ? "" : MAPPER.writeValueAsString(sample(responseSchema, 0));
        return new Verdict(key, Integer.parseInt(status), sample, problems);
    }

    /**
     * Sprawdza, że rekord konsumenta da się wypełnić z odpowiedzi operacji: każdy składnik rekordu jest polem
     * odpowiedzi dostawcy, typ jest zgodny, a składnik prymitywny jest w kontrakcie wymagany (inaczej null wywróci
     * deserializację). Odpowiedź tablicowa jest sprawdzana po elemencie.
     */
    public List<String> responseProblems(String method, String pathTemplate, Class<?> dto) {
        List<String> problems = new ArrayList<>();
        String key = method + " " + pathTemplate;
        JsonNode operation = spec.path("paths").path(pathTemplate).path(method.toLowerCase());
        if (operation.isMissingNode()) {
            return List.of(provider + " nie ma operacji " + key);
        }
        JsonNode schema = schemaOf(operation.path("responses").path(successStatus(operation)).path("content"));
        if (schema == null) {
            return List.of(key + ": kontrakt nie opisuje treści odpowiedzi");
        }
        schema = resolve(schema);
        if ("array".equals(schema.path("type").asString(""))) {
            schema = resolve(schema.path("items"));
        }
        JsonNode properties = schema.path("properties");
        Set<String> required = names(schema.path("required"));
        for (RecordComponent component : dto.getRecordComponents()) {
            String name = component.getName();
            if (!properties.has(name)) {
                problems.add(key + ": " + dto.getSimpleName() + " czyta pole '" + name + "', którego dostawca nie zwraca");
                continue;
            }
            if (component.getType().isPrimitive() && !required.contains(name)) {
                problems.add(key + ": pole '" + name + "' jest prymitywem u konsumenta, ale opcjonalne u dostawcy");
            }
            String expected = jsonType(component.getType());
            String actual = resolve(properties.path(name)).path("type").asString("");
            if (expected != null && !actual.isEmpty() && !expected.equals(actual)
                    && !(expected.equals("number") && actual.equals("integer"))) {
                problems.add(key + ": pole '" + name + "' to " + actual + " u dostawcy, a " + component.getType().getSimpleName()
                        + " u konsumenta");
            }
        }
        return problems;
    }

    private String templateFor(String path) {
        for (var entry : spec.path("paths").properties()) {
            String template = entry.getKey();
            StringBuilder regex = new StringBuilder();
            Matcher variable = PATH_VARIABLE.matcher(template);
            int last = 0;
            while (variable.find()) {
                regex.append(Pattern.quote(template.substring(last, variable.start()))).append("[^/]+");
                last = variable.end();
            }
            regex.append(Pattern.quote(template.substring(last)));
            if (path.matches(regex.toString())) {
                return template;
            }
        }
        return null;
    }

    private void checkQuery(JsonNode operation, String rawQuery, String key, List<String> problems) {
        Set<String> sent = new java.util.LinkedHashSet<>();
        if (rawQuery != null && !rawQuery.isBlank()) {
            for (String pair : rawQuery.split("&")) {
                sent.add(pair.split("=", 2)[0]);
            }
        }
        Set<String> declared = new java.util.LinkedHashSet<>();
        for (JsonNode parameter : operation.path("parameters")) {
            if (!"query".equals(parameter.path("in").asString(""))) {
                continue;
            }
            String name = parameter.path("name").asString("");
            declared.add(name);
            if (parameter.path("required").asBoolean(false) && !sent.contains(name)) {
                problems.add(key + ": adapter nie wysyła wymaganego parametru '" + name + "'");
            }
        }
        for (String name : sent) {
            if (!declared.contains(name)) {
                problems.add(key + ": adapter wysyła parametr '" + name + "', którego kontrakt nie zna");
            }
        }
    }

    private void checkValue(JsonNode schemaNode, JsonNode value, String where, List<String> problems, int depth) {
        JsonNode schema = resolve(schemaNode);
        if (value.isNull() || depth > MAX_DEPTH) {
            return;
        }
        String type = schema.path("type").asString(schema.has("properties") ? "object" : "");
        String format = schema.path("format").asString("");
        switch (type) {
            case "string" -> {
                if (!value.isString()) {
                    problems.add(where + ": oczekiwano tekstu");
                    return;
                }
                String text = value.asString();
                if (schema.has("enum") && !names(schema.path("enum")).contains(text)) {
                    problems.add(where + ": wartość '" + text + "' spoza enum " + names(schema.path("enum")));
                }
                if (format.equals("uuid") && !text.matches("[0-9a-fA-F-]{36}")) {
                    problems.add(where + ": '" + text + "' nie jest UUID");
                }
                if (format.equals("date")) {
                    try {
                        LocalDate.parse(text);
                    } catch (java.time.format.DateTimeParseException e) {
                        problems.add(where + ": '" + text + "' nie jest datą");
                    }
                }
            }
            case "boolean" -> {
                if (!value.isBoolean()) {
                    problems.add(where + ": oczekiwano wartości logicznej");
                }
            }
            case "integer" -> {
                if (!value.isIntegralNumber()) {
                    problems.add(where + ": oczekiwano liczby całkowitej");
                }
            }
            case "number" -> {
                if (!value.isNumber()) {
                    problems.add(where + ": oczekiwano liczby");
                }
            }
            case "array" -> {
                if (!value.isArray()) {
                    problems.add(where + ": oczekiwano tablicy");
                    return;
                }
                int i = 0;
                for (JsonNode item : value) {
                    checkValue(schema.path("items"), item, where + "[" + i++ + "]", problems, depth + 1);
                }
            }
            case "object" -> {
                if (!value.isObject()) {
                    problems.add(where + ": oczekiwano obiektu");
                    return;
                }
                JsonNode properties = schema.path("properties");
                for (var field : value.properties()) {
                    if (!properties.has(field.getKey())) {
                        problems.add(where + ": pole '" + field.getKey() + "' nie istnieje w kontrakcie");
                    } else {
                        checkValue(properties.path(field.getKey()), field.getValue(), where + "." + field.getKey(),
                                problems, depth + 1);
                    }
                }
                for (String name : names(schema.path("required"))) {
                    if (!value.has(name) || value.path(name).isNull()) {
                        problems.add(where + ": brak wymaganego pola '" + name + "'");
                    }
                }
            }
            default -> { /* schemat bez typu: nic do sprawdzenia */ }
        }
    }

    /** Przykład zgodny ze schematem (wszystkie pola, także opcjonalne), żeby adapter przeczytał pełną odpowiedź. */
    private Object sample(JsonNode schemaNode, int depth) {
        JsonNode schema = resolve(schemaNode);
        String type = schema.path("type").asString(schema.has("properties") ? "object" : "");
        String format = schema.path("format").asString("");
        if (schema.has("enum")) {
            return schema.path("enum").get(0).asString();
        }
        return switch (type) {
            case "string" -> switch (format) {
                case "uuid" -> UUID.randomUUID().toString();
                case "date" -> "2000-01-01";
                case "date-time" -> "2000-01-01T00:00:00Z";
                case "email" -> "kontrakt@example.test";
                default -> "tekst";
            };
            case "boolean" -> Boolean.TRUE;
            case "integer" -> 1L;
            case "number" -> new BigDecimal("10.50");
            case "array" -> depth >= MAX_DEPTH ? List.of() : List.of(sample(schema.path("items"), depth + 1));
            case "object" -> {
                Map<String, Object> object = new LinkedHashMap<>();
                if (depth < MAX_DEPTH) {
                    schema.path("properties").properties()
                            .forEach(p -> object.put(p.getKey(), sample(p.getValue(), depth + 1)));
                }
                yield object;
            }
            default -> Map.of();
        };
    }

    private JsonNode resolve(JsonNode schema) {
        JsonNode ref = schema.path("$ref");
        if (ref.isString()) {
            String name = ref.asString().substring(ref.asString().lastIndexOf('/') + 1);
            return spec.path("components").path("schemas").path(name);
        }
        return schema;
    }

    private JsonNode schemaOf(JsonNode content) {
        JsonNode media = content.path("application/json");
        if (media.isMissingNode()) {
            media = content.path("*/*");
        }
        return media.path("schema").isMissingNode() ? null : media.path("schema");
    }

    private static String successStatus(JsonNode operation) {
        for (var response : operation.path("responses").properties()) {
            if (response.getKey().startsWith("2")) {
                return response.getKey();
            }
        }
        return "200";
    }

    private static Set<String> names(JsonNode array) {
        Set<String> out = new java.util.LinkedHashSet<>();
        array.forEach(n -> out.add(n.asString()));
        return out;
    }

    private static String jsonType(Class<?> type) {
        if (type == String.class || type == UUID.class || type == LocalDate.class || type.isEnum()
                || type == java.time.Instant.class || type == java.time.OffsetDateTime.class) {
            return "string";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        if (type == int.class || type == long.class || type == Integer.class || type == Long.class) {
            return "integer";
        }
        if (type == BigDecimal.class || type == double.class || type == Double.class || type == float.class) {
            return "number";
        }
        return null;
    }
}
