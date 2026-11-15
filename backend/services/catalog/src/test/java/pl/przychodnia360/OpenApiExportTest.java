package pl.przychodnia360;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.przychodnia360.testsupport.contract.OpenApiAggregate;

/**
 * Eksportuje kontrakt usługi catalog z działającej aplikacji do api/exports/catalog.json (bez ścieżek wewnętrznych) i
 * przelicza z eksportów usług agregat api/openapi.json (źródło klienta frontendu). Zob. {@link OpenApiAggregate}.
 */
class OpenApiExportTest extends AbstractCatalogApiTest {

    @Test
    void exportsContract() throws Exception {
        var response = get("/api/v1/docs/json");
        assertThat(response.statusCode()).isEqualTo(200);

        OpenApiAggregate.exportAndAggregate("catalog", response.body());
    }
}
