package pl.przychodnia360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.security.PublicEndpoint;
import pl.przychodnia360.identity.application.PasswordResetService;
import pl.przychodnia360.identity.application.RegistrationService;
import pl.przychodnia360.identity.application.RegistrationService.Registration;
import pl.przychodnia360.identity.infrastructure.AuthRateLimiter;

/** Konta bez logowania: rejestracja pacjenta (FR-38) oraz reset i ustawienie hasła (FR-05). */
@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountController {

    public record RegisterPatientRequest(
            @NotBlank @Size(max = 63) String tenantSlug,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 200) String password,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @PastOrPresent LocalDate birthDate) {
    }

    public record PasswordResetRequest(
            @NotBlank @Size(max = 63) String tenantSlug,
            @NotBlank @Email @Size(max = 254) String email) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(max = 200) String newPassword) {
    }

    private final RegistrationService registration;
    private final PasswordResetService passwordReset;
    private final AuthRateLimiter rateLimiter;

    AccountController(RegistrationService registration, PasswordResetService passwordReset,
            AuthRateLimiter rateLimiter) {
        this.registration = registration;
        this.passwordReset = passwordReset;
        this.rateLimiter = rateLimiter;
    }

    @PublicEndpoint
    @PostMapping("/register-patient")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "registerPatient", summary = "Rejestracja konta pacjenta",
            description = "Dostęp: publiczny. Zawsze 202 z pustą odpowiedzią (zajęty e-mail nie jest ujawniany); bez automatycznego logowania.")
    public void register(@Valid @RequestBody RegisterPatientRequest request, HttpServletRequest http) {
        rateLimiter.check("register:" + http.getRemoteAddr());
        registration.registerPatient(new Registration(request.tenantSlug(), request.email(), request.password(),
                request.firstName(), request.lastName(), request.birthDate()));
    }

    @PublicEndpoint
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(operationId = "requestPasswordReset", summary = "Prośba o reset hasła",
            description = "Dostęp: publiczny. Zawsze 202, niezależnie od tego, czy konto istnieje.")
    public void request(@Valid @RequestBody PasswordResetRequest request, HttpServletRequest http) {
        rateLimiter.check("reset:" + http.getRemoteAddr() + ":" + request.email().toLowerCase());
        passwordReset.request(request.tenantSlug(), request.email());
    }

    @PublicEndpoint
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "confirmPasswordReset", summary = "Ustawienie hasła z jednorazowego linku",
            description = "Dostęp: publiczny (token z e-maila). Dotyczy też pierwszego ustawienia hasła pracownika. "
                    + "Po zmianie unieważnia sesje użytkownika.")
    public void confirm(@Valid @RequestBody PasswordResetConfirmRequest request, HttpServletRequest http) {
        rateLimiter.check("reset-confirm:" + http.getRemoteAddr());
        passwordReset.confirm(request.token(), request.newPassword());
    }
}
