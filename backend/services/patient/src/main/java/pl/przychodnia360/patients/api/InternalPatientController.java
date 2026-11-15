package pl.przychodnia360.patients.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.patients.api.PatientFacade.PatientInfo;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ValidationException;

/**
 * Wewnętrzne API pacjentów dla innych usług (S2): odczyt dla widoków wizyt i zapis dla sagi rejestracji. Dostępne
 * wyłącznie dla tożsamości usługi (token usługowy iam: rola SERVICE, {@code aud} = ta usługa, jedna placówka), nie
 * dla żadnej roli użytkownika, i nie routowane przez krawędź. Placówka wynika z tokenu (RLS).
 */
@RestController
public class InternalPatientController {

    private static final int MAX_IDS = 200;

    public record EnsureRequest(@NotBlank String firstName, @NotBlank String lastName, @NotNull @Past LocalDate birthDate,
            String contactEmail, UUID userId) {
    }

    public record LinkAccountRequest(@NotNull UUID userId) {
    }

    private final PatientFacade patients;

    InternalPatientController(PatientFacade patients) {
        this.patients = patients;
    }

    @GetMapping(value = "/api/v1/internal/patients", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'PATIENT_INTERNAL_READ')")
    @Operation(operationId = "internalPatientsByIds", summary = "Pacjenci po identyfikatorach (wewnętrzne)",
            description = "Dostęp: PATIENT_INTERNAL_READ (SERVICE). Nieznane identyfikatory są pomijane.")
    public Collection<PatientInfo> byIds(@RequestParam List<UUID> ids) {
        if (ids.size() > MAX_IDS) {
            throw new ValidationException("Zbyt wiele identyfikatorów (maksymalnie " + MAX_IDS + ").");
        }
        return patients.findAll(ids).values();
    }

    @GetMapping(value = "/api/v1/internal/patients/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'PATIENT_INTERNAL_READ')")
    @Operation(operationId = "internalPatientById", summary = "Pacjent po identyfikatorze (wewnętrzne)",
            description = "Dostęp: PATIENT_INTERNAL_READ (SERVICE).")
    public PatientInfo byId(@PathVariable UUID id) {
        return patients.find(id).orElseThrow(() -> new NotFoundException("Nie znaleziono pacjenta."));
    }

    @PutMapping(value = "/api/v1/internal/patients/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissions.has(authentication, 'PATIENT_INTERNAL_WRITE')")
    @Operation(operationId = "internalEnsurePatient", summary = "Idempotentne założenie pacjenta o zadanym identyfikatorze",
            description = "Dostęp: PATIENT_INTERNAL_WRITE (SERVICE). Saga rejestracji: powtórzenie niczego nie duplikuje.")
    public void ensure(@PathVariable UUID id, @Valid @RequestBody EnsureRequest request) {
        patients.ensure(id, request.firstName(), request.lastName(), request.birthDate(), request.contactEmail(),
                request.userId());
    }

    @PutMapping(value = "/api/v1/internal/patients/{id}/account", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@permissions.has(authentication, 'PATIENT_INTERNAL_WRITE')")
    @Operation(operationId = "internalLinkPatientAccount", summary = "Powiązanie pacjenta z kontem",
            description = "Dostęp: PATIENT_INTERNAL_WRITE (SERVICE).")
    public void linkAccount(@PathVariable UUID id, @Valid @RequestBody LinkAccountRequest request) {
        patients.linkAccount(id, request.userId());
    }
}
