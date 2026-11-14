package pl.przychodnia360.patients.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.web.PageResponse;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.patients.application.PatientService;
import pl.przychodnia360.patients.domain.Patient;
import pl.przychodnia360.shared.NotFoundException;

/** Dane pacjenta: własne (PATIENT) i zarządzane przez rejestratora (FR-17, FR-38, FR-50). */
@RestController
public class PatientController {

    public record PatientResponse(UUID id, String firstName, String lastName, LocalDate birthDate,
            String contactEmail, boolean hasAccount) {

        static PatientResponse of(Patient p) {
            return new PatientResponse(p.id(), p.firstName(), p.lastName(), p.birthDate(), p.contactEmail(),
                    p.userId() != null);
        }
    }

    public record ContactRequest(@Email @Size(max = 254) String contactEmail) {
    }

    public record CreatePatientRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @PastOrPresent LocalDate birthDate,
            @Email @Size(max = 254) String contactEmail) {
    }

    public record SearchRequest(@NotBlank @Size(max = 200) String query, Integer page, Integer size) {
    }

    private final PatientService service;

    PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/v1/me/patient", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'PATIENT_READ_OWN')")
    @Operation(operationId = "getMyPatient", summary = "Dane własne pacjenta", description = "Dostęp: PATIENT_READ_OWN (PATIENT).")
    public PatientResponse mine(@AuthenticationPrincipal AuthenticatedUser user) {
        return PatientResponse.of(service.get(ownPatientId(user)));
    }

    @PatchMapping(value = "/api/v1/me/patient", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'PATIENT_EDIT_OWN')")
    @Operation(operationId = "updateMyPatient", summary = "Edycja własnych danych kontaktowych",
            description = "Dostęp: PATIENT_EDIT_OWN (PATIENT). W 1a tylko e-mail kontaktowy.")
    public PatientResponse updateMine(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ContactRequest request) {
        return PatientResponse.of(service.updateContactEmail(ownPatientId(user), request.contactEmail()));
    }

    @PostMapping(value = "/api/v1/reception/patients", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@permissions.has(authentication, 'PATIENT_REGISTER')")
    @Operation(operationId = "registerPatientByReceptionist", summary = "Założenie rekordu pacjenta",
            description = "Dostęp: PATIENT_REGISTER (RECEPTIONIST). Konto pacjenta zakłada się osobno.")
    public PatientResponse register(@Valid @RequestBody CreatePatientRequest request) {
        UUID id = service.create(request.firstName(), request.lastName(), request.birthDate(), request.contactEmail());
        return PatientResponse.of(service.get(id));
    }

    @PatchMapping(value = "/api/v1/reception/patients/{id}/contact", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'PATIENT_CONTACT_EDIT')")
    @Operation(operationId = "updatePatientContact", summary = "Edycja danych kontaktowych pacjenta",
            description = "Dostęp: PATIENT_CONTACT_EDIT (RECEPTIONIST).")
    public PatientResponse updateContact(@PathVariable UUID id, @Valid @RequestBody ContactRequest request) {
        return PatientResponse.of(service.updateContactEmail(id, request.contactEmail()));
    }

    @PostMapping(value = "/api/v1/reception/patients/search", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@permissions.has(authentication, 'PATIENT_SEARCH')")
    @Operation(operationId = "searchPatients", summary = "Wyszukiwanie pacjentów po imieniu i nazwisku",
            description = "Dostęp: PATIENT_SEARCH (RECEPTIONIST). Fraza w ciele żądania, bez danych medycznych.")
    public PageResponse<PatientResponse> search(@Valid @RequestBody SearchRequest request) {
        return service.search(request.query(), request.page() == null ? 0 : request.page(),
                request.size() == null ? 20 : request.size()).map(PatientResponse::of);
    }

    private static UUID ownPatientId(AuthenticatedUser user) {
        if (user.patientId() == null) {
            throw new NotFoundException("Nie znaleziono danych pacjenta.");
        }
        return user.patientId();
    }
}
