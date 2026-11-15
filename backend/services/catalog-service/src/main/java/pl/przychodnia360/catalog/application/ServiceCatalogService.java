package pl.przychodnia360.catalog.application;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.przychodnia360.catalog.api.CatalogFacade;
import pl.przychodnia360.shared.PerformerType;
import pl.przychodnia360.catalog.application.CatalogRepository.ServiceFilter;
import pl.przychodnia360.catalog.domain.MedicalService;
import pl.przychodnia360.catalog.domain.ServiceCategory;
import pl.przychodnia360.common.web.PageResponse;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ValidationException;

@Service
public class ServiceCatalogService implements CatalogFacade {

    /** Dane do utworzenia i edycji usługi. */
    public record ServiceCommand(UUID categoryId, String name, String description, BigDecimal price,
            int durationMinutes, PerformerType performerType) {
    }

    private static final int MAX_PAGE_SIZE = 100;

    private final CatalogRepository repository;

    ServiceCatalogService(CatalogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicalService> list(ServiceFilter filter, int page, int size) {
        if (filter.minPrice() != null && filter.maxPrice() != null && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new ValidationException("Cena minimalna nie może być większa od maksymalnej.");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        List<MedicalService> items = repository.findServices(filter, safePage * safeSize, safeSize);
        return new PageResponse<>(items, safePage, safeSize, repository.countServices(filter));
    }

    @Transactional(readOnly = true)
    public MedicalService get(UUID id) {
        return repository.findService(id).filter(MedicalService::active)
                .orElseThrow(() -> new NotFoundException("Nie znaleziono usługi."));
    }

    @Transactional
    public MedicalService create(ServiceCommand command) {
        requireCategory(command.categoryId());
        MedicalService created = new MedicalService(Ids.newId(), command.categoryId(), command.name().trim(),
                command.description(), command.price(), command.durationMinutes(), command.performerType(), true, 0);
        repository.insertService(created);
        return created;
    }

    @Transactional
    public MedicalService update(UUID id, ServiceCommand command) {
        MedicalService existing = repository.findService(id)
                .orElseThrow(() -> new NotFoundException("Nie znaleziono usługi."));
        requireCategory(command.categoryId());
        MedicalService updated = new MedicalService(id, command.categoryId(), command.name().trim(),
                command.description(), command.price(), command.durationMinutes(), command.performerType(),
                existing.active(), existing.version());
        repository.updateService(updated);
        return updated;
    }

    /** Usunięcie logiczne; historyczne wizyty zachowują powiązanie z usługą. */
    @Transactional
    public void deactivate(UUID id) {
        MedicalService existing = repository.findService(id)
                .orElseThrow(() -> new NotFoundException("Nie znaleziono usługi."));
        if (existing.active()) {
            repository.updateService(existing.deactivated());
        }
    }

    @Transactional(readOnly = true)
    public List<ServiceCategory> categories() {
        return repository.findCategories();
    }

    @Transactional
    public ServiceCategory createCategory(String name) {
        ServiceCategory created = new ServiceCategory(Ids.newId(), name.trim(), 0);
        repository.insertCategory(created);
        return created;
    }

    @Transactional
    public ServiceCategory renameCategory(UUID id, String name) {
        ServiceCategory existing = repository.findCategory(id)
                .orElseThrow(() -> new NotFoundException("Nie znaleziono kategorii."));
        ServiceCategory renamed = new ServiceCategory(id, name.trim(), existing.version());
        repository.updateCategory(renamed);
        return renamed;
    }

    @Transactional
    public void deleteCategory(UUID id) {
        if (repository.findCategory(id).isEmpty()) {
            throw new NotFoundException("Nie znaleziono kategorii.");
        }
        long active = repository.countActiveServicesInCategory(id);
        if (active > 0) {
            throw new ConflictException("category-in-use",
                    "Kategoria zawiera aktywne usługi (" + active + "). Najpierw je przenieś lub dezaktywuj.");
        }
        try {
            repository.deleteCategory(id);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new ConflictException("category-in-use", "Kategoria jest powiązana z historycznymi usługami.");
        }
    }

    private void requireCategory(UUID categoryId) {
        if (repository.findCategory(categoryId).isEmpty()) {
            throw new ValidationException("Kategoria nie istnieje.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ServiceInfo> service(UUID serviceId) {
        return repository.findService(serviceId).map(s -> new ServiceInfo(s.id(), s.name(), s.price(),
                s.durationMinutes(), s.performerType(), s.active()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ServiceInfo> services(Collection<UUID> ids) {
        return repository.findServices(ids).stream().collect(Collectors.toMap(MedicalService::id,
                s -> new ServiceInfo(s.id(), s.name(), s.price(), s.durationMinutes(), s.performerType(), s.active()),
                (a, b) -> a));
    }
}
