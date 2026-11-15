package pl.przychodnia360.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.przychodnia360.common.security.PublicEndpoint;
import pl.przychodnia360.identity.api.dto.LoginRequest;
import pl.przychodnia360.identity.api.dto.PlatformLoginRequest;
import pl.przychodnia360.identity.api.dto.TokenResponse;
import pl.przychodnia360.identity.application.AuthService;
import pl.przychodnia360.identity.application.TokenService.Tokens;
import pl.przychodnia360.identity.infrastructure.AuthRateLimiter;
import pl.przychodnia360.identity.infrastructure.OriginGuard;
import pl.przychodnia360.shared.UnauthorizedException;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    static final String COOKIE = "p360_rt";
    static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final OriginGuard originGuard;
    private final boolean secureCookie;

    AuthController(AuthService authService, AuthRateLimiter rateLimiter, OriginGuard originGuard,
            @Value("${security.cookie.secure:true}") boolean secureCookie) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.originGuard = originGuard;
        this.secureCookie = secureCookie;
    }

    @PublicEndpoint
    @PostMapping("/login")
    @Operation(operationId = "login", summary = "Logowanie do placówki",
            description = "Dostęp: publiczny. Zwraca token dostępu i ustawia ciasteczko odświeżania.")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        rateLimiter.check("login:" + http.getRemoteAddr() + ":" + request.email().toLowerCase());
        return respond(authService.login(request.tenantSlug(), request.email(), request.password()));
    }

    @PublicEndpoint
    @PostMapping("/platform/login")
    @Operation(operationId = "loginPlatformAdmin", summary = "Logowanie administratora platformy",
            description = "Dostęp: publiczny.")
    public ResponseEntity<TokenResponse> loginPlatform(@Valid @RequestBody PlatformLoginRequest request,
            HttpServletRequest http) {
        rateLimiter.check("platform-login:" + http.getRemoteAddr() + ":" + request.email().toLowerCase());
        return respond(authService.loginPlatformAdmin(request.email(), request.password()));
    }

    @PublicEndpoint
    @PostMapping("/refresh")
    @Operation(operationId = "refreshToken", summary = "Odświeżenie tokenu",
            description = "Dostęp: publiczny, wymaga ciasteczka odświeżania. Rotuje token odświeżania.")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = COOKIE, required = false) String refreshToken, HttpServletRequest http) {
        originGuard.verify(http);
        rateLimiter.check("refresh:" + http.getRemoteAddr());
        if (refreshToken == null) {
            throw new UnauthorizedException("invalid-refresh-token", "Sesja wygasła. Zaloguj się ponownie.");
        }
        return respond(authService.refresh(refreshToken));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/logout")
    @Operation(operationId = "logout", summary = "Wylogowanie",
            description = "Dostęp: zalogowany. Unieważnia rodzinę tokenów odświeżania i usuwa ciasteczko.")
    public ResponseEntity<Void> logout(
            @CookieValue(name = COOKIE, required = false) String refreshToken, HttpServletRequest http) {
        originGuard.verify(http);
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseEntity<TokenResponse> respond(Tokens tokens) {
        Duration maxAge = Duration.between(Instant.now(), tokens.refreshExpiresAt());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), maxAge).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new TokenResponse(tokens.accessToken(), "Bearer", tokens.expiresInSeconds()));
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true).secure(secureCookie).sameSite("Strict").path(COOKIE_PATH).maxAge(maxAge).build();
    }
}
