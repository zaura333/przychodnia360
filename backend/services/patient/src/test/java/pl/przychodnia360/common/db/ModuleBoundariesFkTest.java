package pl.przychodnia360.common.db;

import java.util.Map;
import pl.przychodnia360.PatientIntegrationTest;

/** Mapa tabela -> moduł dla bramki z p360-test-support: w bazie patient są tylko tabele pacjentów i platformy. */
@PatientIntegrationTest
class ModuleBoundariesFkTest extends ModuleBoundariesFkContract {

    @Override
    protected boolean expectsInternalForeignKeys() {
        return false; // jedna tabela danych (patients), bez kluczy obcych
    }

    @Override
    protected Map<String, String> moduleOf() {
        return Map.ofEntries(
                Map.entry("patients", "patients"),
                // tabele platformowe (starter): bez kluczy obcych, kopiowane do każdej usługi
                Map.entry("outbox", "platform"),
                Map.entry("processed_events", "platform"));
    }
}
