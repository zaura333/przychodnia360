package pl.przychodnia360.identity.application;

import java.time.Clock;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.przychodnia360.identity.api.IdentityFacade;
import pl.przychodnia360.identity.api.event.UserDeactivated;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.application.MailSender.MailMessage;
import pl.przychodnia360.identity.domain.AccountToken;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.identity.domain.UserStatus;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.tenant.api.TenantFacade;
import pl.przychodnia360.shared.DomainEventPublisher;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.ValidationException;

@Service
class IdentityService implements IdentityFacade {

    private static final Set<Role> EMPLOYEE_ROLES = Set.of(Role.DOCTOR, Role.MEDICAL_STAFF, Role.RECEPTIONIST);

    private final UserRepository users;
    private final TokenService tokens;
    private final PasswordResetService passwordResets;
    private final TenantFacade tenants;
    private final DomainEventPublisher events;
    private final Clock clock;

    IdentityService(UserRepository users, TokenService tokens, PasswordResetService passwordResets,
            TenantFacade tenants, DomainEventPublisher events, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordResets = passwordResets;
        this.tenants = tenants;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UUID ensureEmployeeAccount(UUID employeeId, String email, Role role) {
        if (!EMPLOYEE_ROLES.contains(role)) {
            throw new ValidationException("Rola nie jest rolą pracownika.");
        }
        UUID tenantId = TenantContext.requireTenant().value();
        String trimmed = email.trim();
        Optional<User> existing = users.findByEmail(tenantId, trimmed);
        if (existing.isPresent()) {
            // powtórzenie sagi (np. po utraconej odpowiedzi): to samo konto, bez drugiego e-maila
            User user = existing.get();
            if (employeeId.equals(user.employeeId()) && user.role() == role) {
                return user.id();
            }
            throw new ConflictException("email-taken", "Konto z tym adresem e-mail już istnieje.");
        }
        User user = User.create(Ids.newId(), tenantId, trimmed, role, null);
        user.linkEmployee(employeeId);
        users.insert(user);
        String raw = passwordResets.issueToken(user, AccountToken.Purpose.SET_PASSWORD);
        String clinic = tenants.name(user.tenantId()).orElse("przychodni");
        passwordResets.sendAfterCommit(new MailMessage(user.email(), "Konto w Przychodni360",
                "Utworzono dla Ciebie konto w placówce " + clinic + ". Ustaw hasło (link ważny 72 godziny):\n"
                        + passwordResets.link(raw)));
        return user.id();
    }

    @Override
    @Transactional
    public void deactivate(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Nie znaleziono konta."));
        if (user.status() == UserStatus.DEACTIVATED) {
            return; // powtórzenie polecenia (saga dezaktywacji pracownika)
        }
        user.deactivate();
        users.update(user);
        tokens.revokeAll(userId);
        events.publish(new UserDeactivated(user.tenantId(), userId, clock.instant()));
    }
}
