package pl.przychodnia360.common.health;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.security.PublicEndpoint;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    public record HealthResponse(String status) {
    }

    private final java.util.Optional<DbProbe> dbProbe;

    HealthController(java.util.Optional<DbProbe> dbProbe) {
        this.dbProbe = dbProbe;
    }

    @PublicEndpoint
    @GetMapping(produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getHealth", summary = "Stan aplikacji (bez bazy)", description = "Dostęp: publiczny.")
    public HealthResponse health() {
        return new HealthResponse("UP");
    }

    /** Sprawdza bazę zapytaniem. Dostępny tylko w profilach local i test (nie istnieje w demo). */
    @PublicEndpoint
    @GetMapping(value = "/db", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getHealthDb", summary = "Stan aplikacji z zapytaniem do bazy (tylko local/test)", description = "Dostęp: publiczny w local/test.")
    public ResponseEntity<HealthResponse> healthDb() {
        if (dbProbe.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return dbProbe.get().ok()
                ? ResponseEntity.ok(new HealthResponse("UP"))
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new HealthResponse("DOWN"));
    }

    /** Sonda bazy istnieje tylko w profilach local i test. */
    @org.springframework.stereotype.Component
    @Profile({"local", "test"})
    static class DbProbe {
        private final JdbcClient jdbc;

        DbProbe(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        boolean ok() {
            try {
                return Integer.valueOf(1).equals(jdbc.sql("SELECT 1").query(Integer.class).single());
            } catch (RuntimeException e) {
                return false;
            }
        }
    }
}
