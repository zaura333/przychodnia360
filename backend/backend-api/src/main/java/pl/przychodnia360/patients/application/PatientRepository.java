package pl.przychodnia360.patients.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pl.przychodnia360.patients.domain.Patient;

public interface PatientRepository {

    void insert(Patient patient);

    void update(Patient patient);

    Optional<Patient> findById(UUID id);

    List<Patient> findByIds(Collection<UUID> ids);

    /** Wyszukiwanie po początkach imienia i nazwiska (każdy fragment frazy musi pasować do imienia lub nazwiska). */
    List<Patient> search(List<String> tokens, int offset, int limit);

    long countSearch(List<String> tokens);
}
