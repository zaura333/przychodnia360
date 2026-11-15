package pl.przychodnia360;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Usługa katalogu (S1, ADR-M14): kategorie i usługi placówki; własna baza p360_catalog, tokeny weryfikowane z JWKS. */
@SpringBootApplication
public class CatalogApplication {

    static {
        // Strefa JVM zawsze UTC (jak w clinic-core); strefa Europe/Warsaw jest sprawą prezentacji i reguł domenowych.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}
