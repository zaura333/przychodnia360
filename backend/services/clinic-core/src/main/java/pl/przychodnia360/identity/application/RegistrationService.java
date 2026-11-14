package pl.przychodnia360.identity.application;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.application.MailSender.MailMessage;
import pl.przychodnia360.identity.domain.PasswordPolicy;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.patients.api.PatientFacade;
import pl.przychodnia360.shared.ConflictException;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.tenant.api.TenantFacade;

/**
 * Samodzielna rejestracja pacjenta (FR-38). Wynik jest zawsze neutralny (202): zajęty e-mail nie jest ujawniany,
 * wtedy na adres idzie wiadomość o istniejącym koncie. Hasło jest haszowane także wtedy, gdy rejestracja się nie
 * powiedzie, żeby czas odpowiedzi nie zdradzał stanu.
 */
@Service
public class RegistrationService {

    /** Dane formularza rejestracji. */
    public record Registration(String tenantSlug, String email, String password, String firstName, String lastName,
            LocalDate birthDate) {
    }

    private final TenantFacade tenants;
    private final PatientFacade patients;
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final PasswordResetService passwordResets;
    private final TransactionTemplate tx;

    RegistrationService(TenantFacade tenants, PatientFacade patients, UserRepository users, PasswordHasher hasher,
            PasswordResetService passwordResets, TransactionTemplate tx) {
        this.tenants = tenants;
        this.patients = patients;
        this.users = users;
        this.hasher = hasher;
        this.passwordResets = passwordResets;
        this.tx = tx;
    }

    public void registerPatient(Registration r) {
        PasswordPolicy.validate(r.password());
        String hash = hasher.hash(r.password());
        Optional<UUID> tenant = tenants.resolveActiveSlug(r.tenantSlug());
        if (tenant.isEmpty()) {
            return;
        }
        try (TenantContext.Handle ignored = TenantContext.open(new TenantId(tenant.get()))) {
            try {
                tx.executeWithoutResult(s -> create(tenant.get(), r, hash));
                passwordResets.sendAfterCommit(new MailMessage(r.email(), "Konto w Przychodni360",
                        "Twoje konto pacjenta zostało utworzone. Możesz się zalogować na stronie przychodni."));
            } catch (ConflictException e) {
                if (!"email-taken".equals(e.problemType())) {
                    throw e;
                }
                passwordResets.sendAfterCommit(new MailMessage(r.email(), "Konto w Przychodni360",
                        "Konto z tym adresem e-mail już istnieje. Jeśli nie pamiętasz hasła, skorzystaj z resetu hasła."));
            }
        }
    }

    private void create(UUID tenantId, Registration r, String hash) {
        UUID patientId = patients.create(r.firstName(), r.lastName(), r.birthDate(), r.email());
        User user = User.create(Ids.newId(), tenantId, r.email().trim(), Role.PATIENT, hash);
        user.linkPatient(patientId);
        users.insert(user);
        patients.linkAccount(patientId, user.id());
    }
}
