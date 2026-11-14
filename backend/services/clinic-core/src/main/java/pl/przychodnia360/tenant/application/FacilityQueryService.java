package pl.przychodnia360.tenant.application;

import java.util.List;
import org.springframework.stereotype.Service;
import pl.przychodnia360.tenant.domain.Facility;

@Service
public class FacilityQueryService {

    private final FacilityRepository repository;

    FacilityQueryService(FacilityRepository repository) {
        this.repository = repository;
    }

    public List<Facility> listActive() {
        return repository.findActive();
    }
}
