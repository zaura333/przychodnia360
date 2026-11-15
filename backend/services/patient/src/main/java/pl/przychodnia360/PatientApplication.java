package pl.przychodnia360;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Usługa pacjentów (S2): rekordy pacjentów placówki; własna baza p360_patient, tokeny weryfikowane z JWKS. */
@SpringBootApplication
public class PatientApplication {

    static {
        // Strefa JVM zawsze UTC (jak w clinic-core); strefa Europe/Warsaw jest sprawą prezentacji i reguł domenowych.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(PatientApplication.class, args);
    }
}
