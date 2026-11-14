package pl.przychodnia360.testsupport.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EventSchemaCompatibilityTest {

    private static final String BASE = """
            {"x-schemaVersion":1,"type":"object",
             "properties":{"id":{"type":"string","format":"uuid"},"at":{"type":"string","format":"date-time"},
                           "note":{"type":"string"}},
             "required":["id","at"]}""";

    private static com.fasterxml.jackson.databind.JsonNode schema(String json) {
        return EventSchemas.parse(json);
    }

    @Test
    void identicalSchemasAreCompatible() {
        assertThat(EventSchemaCompatibility.breakingChanges(schema(BASE), schema(BASE))).isEmpty();
    }

    @Test
    void addingOptionalAndRequiredFieldsIsCompatible() {
        String added = BASE.replace("\"note\":{\"type\":\"string\"}",
                "\"note\":{\"type\":\"string\"},\"extra\":{\"type\":\"integer\"},\"more\":{\"type\":\"string\"}")
                .replace("\"required\":[\"id\",\"at\"]", "\"required\":[\"id\",\"at\",\"more\"]");
        assertThat(EventSchemaCompatibility.breakingChanges(schema(BASE), schema(added))).isEmpty();
    }

    @Test
    void removingAFieldIsBreaking() {
        String removed = BASE.replaceAll(",\\s*\"note\":\\{\"type\":\"string\"\\}", "");
        assertThat(EventSchemaCompatibility.breakingChanges(schema(BASE), schema(removed)))
                .singleElement().asString().contains("usunięto pole 'note'");
    }

    @Test
    void changingTypeOrFormatIsBreaking() {
        String type = BASE.replace("\"note\":{\"type\":\"string\"}", "\"note\":{\"type\":\"integer\"}");
        String format = BASE.replace("\"format\":\"uuid\"", "\"format\":\"date-time\"");
        assertThat(EventSchemaCompatibility.breakingChanges(schema(BASE), schema(type)))
                .singleElement().asString().contains("zmieniono typ pola 'note'");
        assertThat(EventSchemaCompatibility.breakingChanges(schema(BASE), schema(format)))
                .singleElement().asString().contains("zmieniono format pola 'id'");
    }

    @Test
    void aRequiredFieldBecomingOptionalIsBreaking() {
        String optional = BASE.replace("\"required\":[\"id\",\"at\"]", "\"required\":[\"id\"]");
        assertThat(EventSchemaCompatibility.breakingChanges(schema(BASE), schema(optional)))
                .singleElement().asString().contains("pole 'at' przestało być wymagane");
    }

    @Test
    void loweringTheSchemaVersionIsBreaking() {
        String v2 = BASE.replace("\"x-schemaVersion\":1", "\"x-schemaVersion\":2");
        assertThat(EventSchemaCompatibility.breakingChanges(schema(v2), schema(BASE)))
                .singleElement().asString().contains("obniżono x-schemaVersion");
        assertThat(EventSchemaCompatibility.breakingChanges(schema(BASE), schema(v2))).isEmpty();
    }
}
