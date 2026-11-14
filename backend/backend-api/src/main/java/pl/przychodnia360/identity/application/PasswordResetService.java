package pl.przychodnia360.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.identity.application.MailSender.MailMessage;
import pl.przychodnia360.identity.domain.AccountToken;
import pl.przychodnia360.identity.domain.AccountToken.Purpose;
import pl.przychodnia360.identity.domain.PasswordPolicy;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.identity.domain.UserStatus;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.shared.UnprocessableException;
import pl.przychodnia360.tenant.api.TenantFacade;

/**
 * Reset hasła i ustawienie hasła z linku (konta pracowników). Token ma format {@code <tenantId>.<losowa>}, w bazie
 * jest tylko hash. Odpowiedź na prośbę o reset jest taka sama dla istniejącego i nieistniejącego konta.
 */
@Service
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration RESET_TTL = Duration.ofHours(1);
    private static final Duration SET_PASSWORD_TTL = Duration.ofHours(72);

    private final TenantFacade tenants;
    private final UserRepository users;
    private final AccountTokenRepository accountTokens;
    private final TokenService refreshTokens;
    private final PasswordHasher hasher;
    private final MailSender mail;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String baseUrl;

    PasswordResetService(TenantFacade tenants, UserRepository users, AccountTokenRepository accountTokens,
            TokenService refreshTokens, PasswordHasher hasher, MailSender mail, Clock clock, TransactionTemplate tx,
            @Value("${app.public-base-url:http://localhost:5173}") String baseUrl) {
        this.tenants = tenants;
        this.users = users;
        this.accountTokens = accountTokens;
        this.refreshTokens = refreshTokens;
        this.hasher = hasher;
        this.mail = mail;
        this.clock = clock;
        this.tx = tx;
        this.baseUrl = baseUrl;
    }

    /** Prośba o reset. Nic nie ujawnia: brak placówki, brak konta i konto nieaktywne kończą się tak samo. */
    public void request(String tenantSlug, String email) {
        Optional<UUID> tenant = tenants.resolveActiveSlug(tenantSlug);
        if (tenant.isEmpty()) {
            return;
        }
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenant.get()))) {
            tx.executeWithoutResult(s -> users.findByEmail(tenant.get(), email)
                    .filter(u -> u.status() == UserStatus.ACTIVE)
                    .ifPresent(u -> {
                        String raw = issueToken(u, Purpose.RESET);
                        sendAfterCommit(new MailMessage(u.email(), "Reset hasła w Przychodni360",
                                "Otrzymaliśmy prośbę o zmianę hasła. Ustaw nowe hasło (link ważny 1 godzinę):\n"
                                        + link(raw) + "\n\nJeśli to nie Ty, zignoruj tę wiadomość."));
                    }));
        }
    }

    /** Ustawienie hasła z tokenu (reset albo pierwsze ustawienie). Token jest jednorazowy. */
    public void confirm(String rawToken, String newPassword) {
        PasswordPolicy.validate(newPassword);
        UUID tenantId = parseTenant(rawToken);
        String newHash = hasher.hash(newPassword);
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenantId))) {
            tx.executeWithoutResult(s -> {
                Instant now = clock.instant();
                AccountToken token = accountTokens.findByHashForUpdate(TokenService.hash(rawToken))
                        .filter(t -> t.isUsableAt(now)).orElseThrow(PasswordResetService::invalidToken);
                User user = users.findById(token.userId()).filter(u -> u.status() == UserStatus.ACTIVE)
                        .orElseThrow(PasswordResetService::invalidToken);
                user.changePassword(newHash);
                users.update(user);
                accountTokens.invalidateAllForUser(user.id(), now);
                refreshTokens.revokeAll(user.id());
            });
        }
    }

    /**
     * Tworzy token dla użytkownika w bieżącej transakcji i kontekście placówki; zwraca surową wartość do linku.
     */
    String issueToken(User user, Purpose purpose) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = user.tenantId() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Duration ttl = purpose == Purpose.SET_PASSWORD ? SET_PASSWORD_TTL : RESET_TTL;
        accountTokens.insert(new AccountToken(Ids.newId(), user.id(), user.tenantId(), purpose,
                TokenService.hash(raw), clock.instant().plus(ttl), null));
        return raw;
    }

    String link(String rawToken) {
        return baseUrl + "/ustaw-haslo?token=" + rawToken;
    }

    /** Wysyła wiadomość po zatwierdzeniu transakcji (gdy jest aktywna), żeby wycofanie nie zostawiło wysłanego linku. */
    void sendAfterCommit(MailMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    mail.send(message);
                }
            });
        } else {
            mail.send(message);
        }
    }

    private static UUID parseTenant(String raw) {
        if (raw == null || raw.length() > 200 || raw.indexOf('.') < 0) {
            throw invalidToken();
        }
        try {
            return UUID.fromString(raw.substring(0, raw.indexOf('.')));
        } catch (IllegalArgumentException e) {
            throw invalidToken();
        }
    }

    private static UnprocessableException invalidToken() {
        return new UnprocessableException("invalid-or-expired-token", "Link jest nieprawidłowy lub wygasł.");
    }
}
