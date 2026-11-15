package pl.przychodnia360;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.przychodnia360.testsupport.contract.OpenApiAggregate;

/**
 * Eksportuje kontrakt tej usługi z działającej aplikacji (deterministycznie: klucze posortowane, stały serwer i wersja)
 * do api/exports/clinic-core.json i przelicza z eksportów usług agregat api/openapi.json. Pliki są zatwierdzane w
 * repozytorium, a CI sprawdza, że po {@code mvn verify} się nie zmieniły (git diff --exit-code). Lokalny
 * {@code mvn verify} aktualizuje pliki, które trzeba zatwierdzić.
 */
class OpenApiExportTest extends AbstractIntegrationTest {

    @Test
    void exportsContract() throws Exception {
        var response = get("/api/v1/docs/json");
        assertThat(response.statusCode()).isEqualTo(200);

        String json = response.body().replace("\r\n", "\n");
        if (!json.endsWith("\n")) {
            json += "\n";
        }
        assertThat(json)
                .contains("\"/api/v1/health\"")
                .contains("\"/api/v1/appointments\"")
                .doesNotContain("\"/api/v1/facilities\"");

        // eksport usługi (api/exports/clinic-core.json) i przeliczenie agregatu api/openapi.json z eksportów usług
        OpenApiAggregate.exportAndAggregate("clinic-core", json);
    }
}
