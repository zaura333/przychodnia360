package pl.przychodnia360.patients.infrastructure;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.patients.api.PatientRegistry;
import pl.przychodnia360.shared.NotFoundException;

/**
 * Jedyna implementacja {@link PatientRegistry} w iam: zapytania REST do patient-service. Zapisy (saga rejestracji) przy
 * awarii dostają 503, pojedynczy odczyt też. Wywołania idą z tokenem usługowym iam.
 */
@Component
class RemotePatientRegistry implements PatientRegistry {

    private static final ParameterizedTypeReference<PatientInfo> ONE = new ParameterizedTypeReference<>() { };

    private final RemoteServiceClient client;

    RemotePatientRegistry(@Qualifier("patientClient") RemoteServiceClient client) {
        this.client = client;
    }

    @Override
    public void ensure(UUID patientId, String firstName, String lastName, LocalDate birthDate, String contactEmail,
            UUID userId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("birthDate", birthDate.toString());
        body.put("contactEmail", contactEmail);
        body.put("userId", userId == null ? null : userId.toString());
        client.put("/api/v1/internal/patients/{id}", body, patientId);
    }

    @Override
    public Optional<PatientInfo> find(UUID patientId) {
        try {
            return Optional.ofNullable(client.get("/api/v1/internal/patients/{id}", ONE, patientId));
        } catch (NotFoundException e) {
            return Optional.empty();
        }
    }
}
