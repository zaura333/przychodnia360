package pl.przychodnia360;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.shared.ServiceUnavailableException;
import pl.przychodnia360.shared.TenantContext;

/**
 * Atrapa {@link PatientFacade} w testach clinic-core (moduł pacjentów jest w patient-service): rekordy w pamięci,
 * rozdzielone placówkami. Zapis zdalny zatwierdza się niezależnie od transakcji konta, jak w prawdziwej usłudze, więc
 * test sagi rejestracji może wstrzyknąć awarię przed zapisem albo po zapisie (zgubiona odpowiedź).
 */
public class FakePatients implements PatientFacade {

    /** Awaria wstrzykiwana w {@link #ensure}: przed zapisem albo po zapisie (odpowiedź zgubiona). */
    public enum Failure { NONE, BEFORE, AFTER }

    private final Map<UUID, Map<UUID, PatientInfo>> byTenant = new ConcurrentHashMap<>();
    private final AtomicReference<Failure> failure = new AtomicReference<>(Failure.NONE);

    public void failWith(Failure value) {
        failure.set(value);
    }

    public void reset() {
        failure.set(Failure.NONE);
    }

    public Optional<PatientInfo> find(UUID tenant, UUID patientId) {
        return Optional.ofNullable(byTenant.getOrDefault(tenant, Map.of()).get(patientId));
    }

    public long countWithEmail(UUID tenant, String email) {
        return byTenant.getOrDefault(tenant, Map.of()).values().stream()
                .filter(p -> email.equalsIgnoreCase(p.contactEmail())).count();
    }

    public List<PatientInfo> patientsOf(UUID tenant) {
        return List.copyOf(byTenant.getOrDefault(tenant, Map.of()).values());
    }

    private Map<UUID, PatientInfo> current() {
        return byTenant.computeIfAbsent(TenantContext.requireTenant().value(), t -> new ConcurrentHashMap<>());
    }

    @Override
    public void ensure(UUID patientId, String firstName, String lastName, LocalDate birthDate, String contactEmail,
            UUID userId) {
        if (failure.get() == Failure.BEFORE) {
            throw new ServiceUnavailableException("patient niedostępny");
        }
        current().merge(patientId, new PatientInfo(patientId, userId, firstName.trim(), lastName.trim(), birthDate,
                contactEmail), (old, fresh) -> userId == null ? old : new PatientInfo(old.id(), userId, old.firstName(),
                old.lastName(), old.birthDate(), old.contactEmail()));
        if (failure.get() == Failure.AFTER) {
            throw new ServiceUnavailableException("patient nie odpowiada");
        }
    }

    @Override
    public Optional<PatientInfo> find(UUID patientId) {
        return Optional.ofNullable(current().get(patientId));
    }
}
