package pl.przychodnia360.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /**
     * Stały adres serwera i wersja kontraktu: plik api/openapi.json jest zatwierdzany w repozytorium,
     * więc nie może zależeć od portu ani wersji artefaktu.
     */
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Przychodnia360 API").version("v1")
                        .description("Wersja demonstracyjna, dane wyłącznie syntetyczne."))
                .servers(List.of(new Server().url("/")));
    }

    /** Pola odpowiedzi, które mogą być puste (reszta pól odpowiedzi jest zawsze obecna). */
    private static final Map<String, Set<String>> NULLABLE = Map.of(
            "MeResponse", Set.of("tenantId", "tenantName", "employeeId", "patientId", "slotStepMinutes"),
            "DoctorResponse", Set.of("specialization"),
            "EmployeeResponse", Set.of("specialization", "userId"),
            "ServiceResponse", Set.of("description"),
            "PatientResponse", Set.of("contactEmail"),
            "ExceptionResponse", Set.of("startTime", "endTime"),
            "WeeklyEntryResponse", Set.of("validTo"));

    /**
     * springdoc oznacza pola odpowiedzi jako opcjonalne, co daje klientowi typy z samymi opcjonalnymi polami.
     * Odpowiedzi (nazwy kończące się na Response lub View) zawsze zawierają swoje pola, poza wyjątkami z mapy.
     */
    @Bean
    OpenApiCustomizer responseFieldsRequired() {
        return openApi -> openApi.getComponents().getSchemas().forEach((name, schema) -> {
            boolean response = name.endsWith("Response") || name.endsWith("View");
            if (!response || schema.getProperties() == null) {
                return;
            }
            Set<String> nullable = NULLABLE.getOrDefault(name, Set.of());
            List<String> required = schema.getProperties().keySet().stream()
                    .filter(property -> !nullable.contains(property)).sorted().toList();
            schema.setRequired(required);
        });
    }
}
