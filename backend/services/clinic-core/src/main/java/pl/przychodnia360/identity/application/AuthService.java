package pl.przychodnia360.identity.application;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.identity.domain.LoginAttemptPolicy;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.identity.application.TokenService.Tokens;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.shared.UnauthorizedException;
import pl.przychodnia360.tenant.api.TenantFacade;

/**
 * Logowanie, odświeżanie i wylogowanie. Hasło jest weryfikowane poza transakcją (Argon2id jest kosztowny, nie
 * trzymamy połączenia z bazą). Licznik nieudanych prób zapisujemy we własnej transakcji, zatwierdzanej przed
 * rzuceniem wyjątku 401 (inaczej blokada konta nigdy by nie zadziałała). Wszystkie błędy logowania są
 * nieodróżnialne (ADR-B02).
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final TenantFacade tenants;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TokenService tokens;
    private final LoginAttemptPolicy attemptPolicy;
    private final Clock clock;
    private final TransactionTemplate tx;

    AuthService(TenantFacade tenants, UserRepository users, PasswordHasher hasher, TokenService tokens,
            LoginAttemptPolicy attemptPolicy, Clock clock, TransactionTemplate tx) {
        this.tenants = tenants;
        this.users = users;
        this.hasher = hasher;
        this.tokens = tokens;
        this.attemptPolicy = attemptPolicy;
        this.clock = clock;
        this.tx = tx;
    }

    public Tokens login(String tenantSlug, String email, String password) {
        Optional<UUID> tenant = tenants.resolveActiveSlug(tenantSlug);
        if (tenant.isEmpty()) {
            hasher.burnTime(password);
            throw invalid();
        }
        return authenticate(tenant.get(), email, password);
    }

    public Tokens loginPlatformAdmin(String email, String password) {
        return authenticate(null, email, password);
    }

    public Tokens refresh(String rawRefreshToken) {
        return tokens.rotate(rawRefreshToken);
    }

    public void logout(String rawRefreshToken) {
        tokens.revoke(rawRefreshToken);
    }

    /** tenantId == null oznacza logowanie administratora platformy (zakres platformy). */
    private Tokens authenticate(UUID tenantId, String email, String password) {
        try (TenantContext.Handle ignored = open(tenantId)) {
            User user = tx.execute(s -> find(tenantId, email));
            if (user == null || !user.canLogin() || user.isLocked(clock.instant())) {
                hasher.burnTime(password);
                if (user != null) {
                    log.warn("Odrzucone logowanie (konto zablokowane lub nieaktywne): userId={} tenantId={}",
                            user.id(), user.tenantId());
                }
                throw invalid();
            }
            if (!hasher.matches(password, user.passwordHash())) {
                recordFailure(tenantId, user.id());
                throw invalid();
            }
            return tx.execute(s -> {
                User fresh = users.findById(user.id()).orElseThrow();
                fresh.registerSuccess();
                if (hasher.needsRehash(fresh.passwordHash())) {
                    fresh.changePassword(hasher.hash(password));
                }
                users.update(fresh);
                return tokens.issueFor(fresh, null);
            });
        }
    }

    private void recordFailure(UUID tenantId, UUID userId) {
        try {
            doRecordFailure(tenantId, userId);
        } catch (pl.przychodnia360.shared.ConflictException e) {
            log.debug("Równoległa aktualizacja licznika prób logowania, pomijam: userId={}", userId);
        }
    }

    private void doRecordFailure(UUID tenantId, UUID userId) {
        tx.executeWithoutResult(s -> users.findById(userId).ifPresent(fresh -> {
            boolean wasLocked = fresh.isLocked(clock.instant());
            fresh.registerFailedAttempt(attemptPolicy, clock.instant());
            users.update(fresh);
            if (!wasLocked && fresh.isLocked(clock.instant())) {
                log.warn("Konto zablokowane po nieudanych logowaniach: userId={} tenantId={}", userId, tenantId);
            }
        }));
    }

    private User find(UUID tenantId, String email) {
        return (tenantId == null ? users.findPlatformAdminByEmail(email) : users.findByEmail(tenantId, email))
                .orElse(null);
    }

    private static TenantContext.Handle open(UUID tenantId) {
        return tenantId == null ? TenantContext.openPlatformScope() : TenantContext.open(new TenantId(tenantId));
    }

    private static UnauthorizedException invalid() {
        return new UnauthorizedException("invalid-credentials", "Niepoprawny adres e-mail lub hasło.");
    }
}
