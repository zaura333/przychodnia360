package pl.przychodnia360.patients.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.przychodnia360.common.web.PageResponse;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.patients.api.PatientRegistered;
import pl.przychodnia360.patients.api.PatientUpdated;
import pl.przychodnia360.patients.domain.Patient;
import pl.przychodnia360.shared.DomainEventPublisher;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.ValidationException;

@Service
public class PatientService implements PatientFacade {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_TOKENS = 4;

    private final PatientRepository repository;
    private final DomainEventPublisher events;
    private final Clock clock;

    PatientService(PatientRepository repository, DomainEventPublisher events, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID create(String firstName, String lastName, LocalDate birthDate, String contactEmail) {
        Patient patient = new Patient(Ids.newId(), null, firstName.trim(), lastName.trim(), birthDate,
                blankToNull(contactEmail), 0);
        repository.insert(patient);
        events.publish(new PatientRegistered(TenantContext.requireTenant().value(), patient.id(), clock.instant()));
        return patient.id();
    }

    @Override
    @Transactional
    public void ensure(UUID patientId, String firstName, String lastName, LocalDate birthDate, String contactEmail,
            UUID userId) {
        Optional<Patient> existing = repository.findById(patientId);
        if (existing.isPresent()) {
            // powiązanie z kontem jest ustawiane na konto z ostatniej rejestracji: po wycofanej próbie rekord może
            // wskazywać konto, które już nie istnieje
            if (userId != null && !userId.equals(existing.get().userId())) {
                repository.update(existing.get().withUserId(userId));
            }
            return;
        }
        repository.insert(new Patient(patientId, userId, firstName.trim(), lastName.trim(), birthDate,
                blankToNull(contactEmail), 0));
        events.publish(new PatientRegistered(TenantContext.requireTenant().value(), patientId, clock.instant()));
    }

    @Override
    @Transactional
    public void linkAccount(UUID patientId, UUID userId) {
        Patient patient = repository.findById(patientId).orElseThrow(() -> new NotFoundException("Nie znaleziono pacjenta."));
        repository.update(patient.withUserId(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientInfo> find(UUID patientId) {
        return repository.findById(patientId).map(PatientService::info);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PatientInfo> findAll(Collection<UUID> ids) {
        return repository.findByIds(ids).stream()
                .collect(Collectors.toMap(Patient::id, PatientService::info, (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public Patient get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Nie znaleziono pacjenta."));
    }

    @Transactional
    public Patient updateContactEmail(UUID id, String email) {
        Patient updated = get(id).withContactEmail(blankToNull(email));
        repository.update(updated);
        events.publish(new PatientUpdated(TenantContext.requireTenant().value(), id, clock.instant()));
        return repository.findById(id).orElseThrow();
    }

    /** Fraza dzielona na fragmenty (maks. 4); pusta fraza to błąd, żeby nie zwracać całej bazy pacjentów. */
    @Transactional(readOnly = true)
    public PageResponse<Patient> search(String query, int page, int size) {
        List<String> tokens = Arrays.stream(query == null ? new String[0] : query.trim().split("\\s+"))
                .filter(t -> !t.isBlank()).limit(MAX_TOKENS).toList();
        if (tokens.isEmpty()) {
            throw new ValidationException("Podaj imię lub nazwisko pacjenta.");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        return new PageResponse<>(repository.search(tokens, safePage * safeSize, safeSize), safePage, safeSize,
                repository.countSearch(tokens));
    }

    private static PatientInfo info(Patient p) {
        return new PatientInfo(p.id(), p.userId(), p.firstName(), p.lastName(), p.birthDate(), p.contactEmail());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
