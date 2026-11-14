package pl.przychodnia360.identity.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.przychodnia360.identity.api.IdentityFacade;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.application.MailSender.MailMessage;
import pl.przychodnia360.identity.domain.AccountToken;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.tenant.api.TenantFacade;
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

    IdentityService(UserRepository users, TokenService tokens, PasswordResetService passwordResets,
            TenantFacade tenants) {
        this.users = users;
        this.tokens = tokens;
        this.passwordResets = passwordResets;
        this.tenants = tenants;
    }

    @Override
    @Transactional
    public UUID createEmployeeAccount(String email, Role role) {
        if (!EMPLOYEE_ROLES.contains(role)) {
            throw new ValidationException("Rola nie jest rolą pracownika.");
        }
        User user = User.create(Ids.newId(), TenantContext.requireTenant().value(), email.trim(), role, null);
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
    public void linkEmployee(UUID userId, UUID employeeId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Nie znaleziono konta."));
        user.linkEmployee(employeeId);
        users.update(user);
    }

    @Override
    @Transactional
    public void deactivate(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("Nie znaleziono konta."));
        user.deactivate();
        users.update(user);
        tokens.revokeAll(userId);
    }
}
