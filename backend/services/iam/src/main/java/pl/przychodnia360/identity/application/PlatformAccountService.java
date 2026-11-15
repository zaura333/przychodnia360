package pl.przychodnia360.identity.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.application.MailSender.MailMessage;
import pl.przychodnia360.identity.domain.AccountToken;
import pl.przychodnia360.identity.domain.PasswordPolicy;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.tenant.api.TenantFacade;
import pl.przychodnia360.tenant.api.TenantFacade.TenantInfo;

/** Konta zakładane przez platformę: administratorzy placówek (FR-43) i pierwszy administrator platformy. */
@Service
public class PlatformAccountService {

    private static final Logger log = LoggerFactory.getLogger(PlatformAccountService.class);

    private final TenantFacade tenants;
    private final UserRepository users;
    private final PasswordResetService passwordResets;
    private final PasswordHasher hasher;
    private final TransactionTemplate tx;

    PlatformAccountService(TenantFacade tenants, UserRepository users, PasswordResetService passwordResets,
            PasswordHasher hasher, TransactionTemplate tx) {
        this.tenants = tenants;
        this.users = users;
        this.passwordResets = passwordResets;
        this.hasher = hasher;
        this.tx = tx;
    }

    /**
     * Zakłada administratora placówki bez hasła i wysyła mu link ustawienia hasła (72 h). Wywołanie w zakresie
     * platformy; konto powstaje funkcją SECURITY DEFINER, a token i wiadomość w kontekście tej placówki.
     */
    public UUID createTenantAdmin(UUID tenantId, String email) {
        TenantInfo tenant = tenants.info(tenantId).orElseThrow(() -> new NotFoundException("Nie znaleziono placówki."));
        UUID userId = Ids.newId();
        tx.executeWithoutResult(s -> users.createTenantAdmin(userId, tenantId, email.trim()));
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenantId))) {
            tx.executeWithoutResult(s -> {
                User user = users.findById(userId).orElseThrow();
                String raw = passwordResets.issueToken(user, AccountToken.Purpose.SET_PASSWORD);
                passwordResets.sendAfterCommit(new MailMessage(user.email(), "Konto administratora w Przychodni360",
                        "Utworzono dla Ciebie konto administratora placówki " + tenant.name()
                                + ". Ustaw hasło (link ważny 72 godziny):\n" + passwordResets.link(raw)));
            });
        }
        return userId;
    }

    /**
     * Zakłada pierwszego administratora platformy, jeśli konto o tym adresie nie istnieje (idempotentnie).
     * Hasło pochodzi z konfiguracji uruchomienia jednorazowego, nigdy z migracji.
     */
    public boolean ensurePlatformAdmin(String email, String password) {
        PasswordPolicy.validate(password);
        String hash = hasher.hash(password);
        try (TenantContext.Handle ignored = TenantContext.openPlatformScope()) {
            Boolean created = tx.execute(s -> {
                if (users.findPlatformAdminByEmail(email).isPresent()) {
                    return false;
                }
                users.insert(User.create(Ids.newId(), null, email.trim(), Role.PLATFORM_ADMIN, hash));
                return true;
            });
            log.info("Administrator platformy: {}", Boolean.TRUE.equals(created) ? "utworzony" : "już istnieje");
            return Boolean.TRUE.equals(created);
        }
    }
}
