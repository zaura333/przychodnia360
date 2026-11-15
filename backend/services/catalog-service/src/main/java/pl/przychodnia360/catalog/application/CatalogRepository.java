package pl.przychodnia360.catalog.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pl.przychodnia360.shared.PerformerType;
import pl.przychodnia360.catalog.domain.MedicalService;
import pl.przychodnia360.catalog.domain.ServiceCategory;

public interface CatalogRepository {

    /** Filtr listy usług (pola null są pomijane); lista dotyczy tylko usług aktywnych. */
    record ServiceFilter(UUID categoryId, BigDecimal minPrice, BigDecimal maxPrice, PerformerType performerType) {
    }

    List<MedicalService> findServices(ServiceFilter filter, int offset, int limit);

    long countServices(ServiceFilter filter);

    Optional<MedicalService> findService(UUID id);

    List<MedicalService> findServices(Collection<UUID> ids);

    void insertService(MedicalService service);

    void updateService(MedicalService service);

    List<ServiceCategory> findCategories();

    Optional<ServiceCategory> findCategory(UUID id);

    void insertCategory(ServiceCategory category);

    void updateCategory(ServiceCategory category);

    /** Usunięcie kategorii; false, gdy nie istnieje. */
    boolean deleteCategory(UUID id);

    long countActiveServicesInCategory(UUID categoryId);
}
