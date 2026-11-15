package pl.przychodnia360;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.api.IdentityFacade;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.ServiceUnavailableException;
import pl.przychodnia360.shared.TenantContext;

/**
 * Atrapa iam w testach scheduling (S3): konta pracowników w pamięci z tą samą idempotencją co wewnętrzne API iam
 * (powtórzenie dla tego samego pracownika zwraca to samo konto, e-mail innego konta to 409). Pozwala też zasymulować
 * awarię iam.
 */
public class FakeIdentity implements IdentityFacade {

    /** Konto pracownika w atrapie. */
    public record Account(UUID userId, UUID tenantId, String email, Role role, UUID employeeId, boolean active) {
    }

    private final Map<UUID, Account> byUser = new ConcurrentHashMap<>();
    private final Set<UUID> deactivated = ConcurrentHashMap.newKeySet();
    private volatile boolean down;

    public void down(boolean value) {
        down = value;
    }

    public Optional<Account> account(UUID userId) {
        Account account = byUser.get(userId);
        return account == null ? Optional.empty()
                : Optional.of(new Account(account.userId(), account.tenantId(), account.email(), account.role(),
                        account.employeeId(), !deactivated.contains(userId)));
    }

    @Override
    public UUID ensureEmployeeAccount(UUID employeeId, String email, Role role) {
        if (down) {
            throw new ServiceUnavailableException("Usługa iam nie odpowiada. Spróbuj ponownie za chwilę.");
        }
        UUID tenantId = TenantContext.requireTenant().value();
        for (Account account : byUser.values()) {
            if (account.tenantId().equals(tenantId) && account.email().equalsIgnoreCase(email.trim())) {
                if (employeeId.equals(account.employeeId()) && account.role() == role) {
                    return account.userId();
                }
                throw new ConflictException("email-taken", "Konto z tym adresem e-mail już istnieje.");
            }
        }
        UUID userId = UUID.randomUUID();
        byUser.put(userId, new Account(userId, tenantId, email.trim(), role, employeeId, true));
        return userId;
    }

    @Override
    public void deactivate(UUID userId) {
        if (down) {
            throw new ServiceUnavailableException("Usługa iam nie odpowiada. Spróbuj ponownie za chwilę.");
        }
        deactivated.add(userId);
    }
}
