package pl.przychodnia360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.security.PublicEndpoint;
import pl.przychodnia360.identity.infrastructure.JwtService;

/** Klucze publiczne do weryfikacji tokenów dostępu (JWKS, RFC 7517). Inne usługi pobierają je tym adresem. */
@RestController
public class JwksController {

    private final JwtService jwt;

    JwksController(JwtService jwt) {
        this.jwt = jwt;
    }

    @GetMapping(value = "/api/v1/auth/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    @PublicEndpoint
    @Operation(operationId = "getJwks", summary = "Klucze publiczne tokenów (JWKS)",
            description = "Dostęp: publiczny. Zawiera wyłącznie parametry publiczne kluczy RSA (aktywnych i wycofywanych).")
    public Map<String, Object> jwks() {
        return jwt.jwks();
    }
}
