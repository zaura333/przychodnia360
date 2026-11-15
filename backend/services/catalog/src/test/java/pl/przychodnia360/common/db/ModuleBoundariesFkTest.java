package pl.przychodnia360.common.db;

import java.util.Map;
import pl.przychodnia360.CatalogIntegrationTest;

/** Mapa tabela -> moduł dla bramki z p360-test-support: w bazie catalog są tylko tabele katalogu i platformy. */
@CatalogIntegrationTest
class ModuleBoundariesFkTest extends ModuleBoundariesFkContract {

    @Override
    protected Map<String, String> moduleOf() {
        return Map.ofEntries(
                Map.entry("service_categories", "catalog"),
                Map.entry("services", "catalog"),
                // tabele platformowe (starter): bez kluczy obcych, kopiowane do każdej usługi
                Map.entry("outbox", "platform"),
                Map.entry("processed_events", "platform"));
    }
}
