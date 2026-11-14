package pl.przychodnia360.tenant.application;

import java.util.List;
import pl.przychodnia360.tenant.domain.Facility;

public interface FacilityRepository {

    List<Facility> findActive();
}
