package pl.przychodnia360.patients.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.patients.api.PatientFacade.PatientInfo;
import pl.przychodnia360.shared.ForbiddenException;
import pl.przychodnia360.shared.NotFoundException;
import pl.przychodnia360.shared.ValidationException;

/**
 * Wewnętrzne API pacjentów dla innych usług (zdalna implementacja {@link PatientFacade}, S2): imiona i nazwiska do
 * widoków wizyt. Nie jest routowane przez krawędź, a placówka wynika z przekazanego tokenu (RLS). Dane osobowe widzi
 * personel placówki; pacjent tylko własny rekord (claim pid), więc przekazany token pacjenta nie otwiera cudzych danych.
 */
@RestController
public class InternalPatientController {

    private static final int MAX_IDS = 200;

    private final PatientFacade patients;

    InternalPatientController(PatientFacade patients) {
        this.patients = patients;
    }

    @GetMapping(value = "/api/v1/internal/patients", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "internalPatientsByIds", summary = "Pacjenci po identyfikatorach (wewnętrzne)",
            description = "Dostęp: personel placówki; PATIENT tylko własny rekord. Nieznane identyfikatory są pomijane.")
    public Collection<PatientInfo> byIds(@RequestParam List<UUID> ids, @AuthenticationPrincipal AuthenticatedUser caller) {
        if (ids.size() > MAX_IDS) {
            throw new ValidationException("Zbyt wiele identyfikatorów (maksymalnie " + MAX_IDS + ").");
        }
        if (caller.role() == Role.PATIENT && !(caller.patientId() != null && ids.stream().allMatch(caller.patientId()::equals))) {
            throw new ForbiddenException("Pacjent ma dostęp tylko do własnego rekordu.");
        }
        return patients.findAll(ids).values();
    }

    @GetMapping(value = "/api/v1/internal/patients/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "internalPatientById", summary = "Pacjent po identyfikatorze (wewnętrzne)",
            description = "Dostęp: personel placówki; PATIENT tylko własny rekord.")
    public PatientInfo byId(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser caller) {
        if (caller.role() == Role.PATIENT && !id.equals(caller.patientId())) {
            throw new ForbiddenException("Pacjent ma dostęp tylko do własnego rekordu.");
        }
        return patients.find(id).orElseThrow(() -> new NotFoundException("Nie znaleziono pacjenta."));
    }
}
