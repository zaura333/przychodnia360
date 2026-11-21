package pl.przychodnia360.patients.infrastructure;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import pl.przychodnia360.common.http.RemoteServiceClient;
import pl.przychodnia360.patients.api.PatientDirectory;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ServiceUnavailableException;

/**
 * Jedyna implementacja {@link PatientDirectory} w {@code scheduling} (S2): zapytania REST do patient-service. Widoki
 * (wiele pacjentów) degradują się do pustych nazw przy awarii; pojedynczy odczyt (rezerwacja) i zapis (seed) dostają 503.
 */
@Component
class RemotePatientDirectory implements PatientDirectory {

    private static final Logger log = LoggerFactory.getLogger(RemotePatientDirectory.class);
    private static final ParameterizedTypeReference<PatientInfo> ONE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<PatientInfo>> MANY = new ParameterizedTypeReference<>() { };

    private final RemoteServiceClient client;

    RemotePatientDirectory(@Qualifier("patientClient") RemoteServiceClient client) {
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

    @Override
    public Map<UUID, PatientInfo> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        String csv = ids.stream().map(UUID::toString).collect(Collectors.joining(","));
        try {
            List<PatientInfo> patients = client.get("/api/v1/internal/patients?ids={ids}", MANY, csv);
            return patients.stream().collect(Collectors.toMap(PatientInfo::id, p -> p, (a, b) -> a));
        } catch (ServiceUnavailableException e) {
            log.warn("Usługa pacjentów niedostępna, widok bez nazwisk: {}", e.getMessage());
            return Map.of();
        }
    }
}
