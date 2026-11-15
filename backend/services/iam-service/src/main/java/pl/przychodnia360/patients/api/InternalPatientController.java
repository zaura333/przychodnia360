package pl.przychodnia360.patients.api;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.internal.InternalApi;
import pl.przychodnia360.patients.api.PatientFacade.PatientInfo;

/**
 * Wewnętrzne API pacjentów dla scheduling-service (ADR-M07). Dostęp ma każdy zalogowany użytkownik placówki, bo
 * scheduling-service sprawdził już uprawnienie do rezerwacji lub widoku wizyt; RLS ogranicza wynik do placówki z
 * tokenu, a ścieżka nie jest wystawiona przez krawędź.
 */
@Hidden
@RestController
public class InternalPatientController {

    public record IdsRequest(@NotNull @Size(max = 500) List<@NotNull UUID> ids) {
    }

    private final PatientFacade patients;

    InternalPatientController(PatientFacade patients) {
        this.patients = patients;
    }

    @PreAuthorize("@permissions.has(authentication, 'TENANT_READ')")
    @PostMapping(value = InternalApi.PREFIX + "/patients/lookup", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PatientInfo> lookup(@Valid @RequestBody IdsRequest request) {
        return List.copyOf(patients.findAll(request.ids()).values());
    }
}
