package pl.przychodnia360;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.remote.CatalogFacade;
import pl.przychodnia360.remote.IdentityFacade;
import pl.przychodnia360.remote.PatientFacade;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.PerformerType;
import pl.przychodnia360.shared.TenantContext;

/**
 * Atrapy catalog-service i iam-service dla testów scheduling-service. Dane są rozdzielone po placówkach (jak RLS
 * w usługach źródłowych), więc test izolacji widzi cudzą usługę i cudzego pacjenta jako nieistniejących.
 * Prawdziwe wywołania HTTP sprawdza HttpRemoteClientsTest, a całość e2e na trzech usługach.
 */
public final class RemoteFakes {

    private RemoteFakes() {
    }

    private static UUID tenant() {
        return TenantContext.requireTenant().value();
    }

    public static class FakeCatalog implements CatalogFacade {
        private final Map<UUID, Map<UUID, ServiceInfo>> byTenant = new ConcurrentHashMap<>();

        public ServiceInfo add(UUID tenantId, String name, int minutes, PerformerType performer) {
            ServiceInfo info = new ServiceInfo(UUID.randomUUID(), name, new BigDecimal("100.00"), minutes, performer, true);
            byTenant.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>()).put(info.id(), info);
            return info;
        }

        public void deactivate(UUID tenantId, UUID serviceId) {
            byTenant.get(tenantId).computeIfPresent(serviceId, (id, s) -> new ServiceInfo(s.id(), s.name(), s.price(),
                    s.durationMinutes(), s.performerType(), false));
        }

        @Override
        public Optional<ServiceInfo> service(UUID serviceId) {
            return Optional.ofNullable(byTenant.getOrDefault(tenant(), Map.of()).get(serviceId));
        }

        @Override
        public Map<UUID, ServiceInfo> services(Collection<UUID> ids) {
            Map<UUID, ServiceInfo> all = byTenant.getOrDefault(tenant(), Map.of());
            Map<UUID, ServiceInfo> found = new HashMap<>();
            ids.forEach(id -> Optional.ofNullable(all.get(id)).ifPresent(s -> found.put(id, s)));
            return found;
        }
    }

    public static class FakePatients implements PatientFacade {
        private final Map<UUID, Map<UUID, PatientInfo>> byTenant = new ConcurrentHashMap<>();

        public PatientInfo add(UUID tenantId, UUID userId, String firstName, String lastName) {
            PatientInfo info = new PatientInfo(UUID.randomUUID(), userId, firstName, lastName, LocalDate.of(1990, 1, 1),
                    null);
            byTenant.computeIfAbsent(tenantId, t -> new ConcurrentHashMap<>()).put(info.id(), info);
            return info;
        }

        @Override
        public Optional<PatientInfo> find(UUID patientId) {
            return Optional.ofNullable(byTenant.getOrDefault(tenant(), Map.of()).get(patientId));
        }

        @Override
        public Map<UUID, PatientInfo> findAll(Collection<UUID> ids) {
            Map<UUID, PatientInfo> all = byTenant.getOrDefault(tenant(), Map.of());
            Map<UUID, PatientInfo> found = new HashMap<>();
            ids.forEach(id -> Optional.ofNullable(all.get(id)).ifPresent(p -> found.put(id, p)));
            return found;
        }
    }

    /** Konto pracownika zapamiętane przez atrapę iam: rola, powiązany pracownik i stan. */
    public record Account(UUID userId, UUID tenantId, String email, Role role, UUID employeeId, boolean active) {
    }

    public static class FakeIdentity implements IdentityFacade {
        private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
        private final List<UUID> deactivated = new CopyOnWriteArrayList<>();

        @Override
        public UUID createEmployeeAccount(String email, Role role) {
            UUID tenant = tenant();
            if (accounts.values().stream().anyMatch(a -> a.tenantId().equals(tenant) && a.email().equalsIgnoreCase(email))) {
                // jak iam-service: unikalny e-mail w placówce
                throw new ConflictException("email-taken", "Konto z tym adresem e-mail już istnieje.");
            }
            UUID id = UUID.randomUUID();
            accounts.put(id, new Account(id, tenant(), email, role, null, true));
            return id;
        }

        @Override
        public void linkEmployee(UUID userId, UUID employeeId) {
            accounts.computeIfPresent(userId, (id, a) -> new Account(id, a.tenantId(), a.email(), a.role(), employeeId,
                    a.active()));
        }

        @Override
        public void deactivate(UUID userId) {
            deactivated.add(userId);
            accounts.computeIfPresent(userId, (id, a) -> new Account(id, a.tenantId(), a.email(), a.role(),
                    a.employeeId(), false));
        }

        public Optional<Account> account(UUID userId) {
            return Optional.ofNullable(accounts.get(userId));
        }

        public List<UUID> deactivated() {
            return deactivated;
        }
    }

    @TestConfiguration
    public static class Config {
        @Bean
        @Primary
        FakeCatalog fakeCatalog() {
            return new FakeCatalog();
        }

        @Bean
        @Primary
        FakePatients fakePatients() {
            return new FakePatients();
        }

        @Bean
        @Primary
        FakeIdentity fakeIdentity() {
            return new FakeIdentity();
        }
    }
}
