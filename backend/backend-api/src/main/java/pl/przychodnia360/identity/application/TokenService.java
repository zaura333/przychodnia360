package pl.przychodnia360.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import pl.przychodnia360.common.security.AuthenticatedUser;
import pl.przychodnia360.common.security.Role;
import pl.przychodnia360.identity.domain.RefreshToken;
import pl.przychodnia360.identity.domain.User;
import pl.przychodnia360.identity.domain.UserStatus;
import pl.przychodnia360.identity.infrastructure.JwtService;
import pl.przychodnia360.shared.Ids;
import pl.przychodnia360.shared.TenantContext;
import pl.przychodnia360.shared.TenantId;
import pl.przychodnia360.shared.UnauthorizedException;

/**
 * Tokeny dostępu (JWT) i odświeżania. Token odświeżania ma format {@code <tenantId|platform>.<familyId>.<losowa>}:
 * tenant i rodzina są jawne (pozwalają ustawić kontekst RLS przed odczytem), wartość jest tajna, a w bazie
 * jest tylko hash SHA-256 całego tokenu. Ponowne użycie zużytego tokenu unieważnia całą rodzinę.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final String PLATFORM = "platform";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Para tokenów zwracana klientowi. */
    public record Tokens(String accessToken, long expiresInSeconds, String refreshToken, Instant refreshExpiresAt) {
    }

    private record Parsed(UUID tenantId, UUID familyId) {
        boolean platform() {
            return tenantId == null;
        }
    }

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final JwtService jwt;
    private final Clock clock;
    private final TransactionTemplate tx;

    TokenService(RefreshTokenRepository refreshTokens, UserRepository users, JwtService jwt, Clock clock,
            TransactionTemplate tx) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.jwt = jwt;
        this.clock = clock;
        this.tx = tx;
    }

    public static AuthenticatedUser principal(User user) {
        return new AuthenticatedUser(user.id(), user.tenantId(), user.role(), user.employeeId(), user.patientId());
    }

    /** Wystawia parę tokenów; wywołać w transakcji z kontekstem użytkownika. Nowa rodzina, gdy familyId jest null. */
    public Tokens issueFor(User user, UUID familyId) {
        UUID family = familyId == null ? Ids.newId() : familyId;
        Instant now = clock.instant();
        Instant expires = now.plus(jwt.refreshTtl());
        String raw = (user.tenantId() == null ? PLATFORM : user.tenantId().toString()) + "." + family + "."
                + randomPart();
        refreshTokens.insert(new RefreshToken(Ids.newId(), family, user.id(), user.tenantId(), hash(raw), expires,
                null, null));
        return new Tokens(jwt.issue(principal(user)), jwt.accessTtl().toSeconds(), raw, expires);
    }

    /** Rotacja tokenu odświeżania. Każdy błąd to ten sam 401; ponowne użycie unieważnia rodzinę (commit przed wyjątkiem). */
    public Tokens rotate(String raw) {
        Parsed parsed = parse(raw);
        Tokens result;
        try (TenantContext.Handle ignored = open(parsed)) {
            result = tx.execute(status -> doRotate(raw, parsed));
        }
        if (result == null) {
            throw new UnauthorizedException("invalid-refresh-token", "Sesja wygasła. Zaloguj się ponownie.");
        }
        return result;
    }

    private Tokens doRotate(String raw, Parsed parsed) {
        Instant now = clock.instant();
        Optional<RefreshToken> found = refreshTokens.findByHashForUpdate(hash(raw));
        if (found.isEmpty() || !found.get().familyId().equals(parsed.familyId())) {
            return null;
        }
        RefreshToken token = found.get();
        if (token.isReuse()) {
            refreshTokens.revokeFamily(token.familyId(), now);
            log.warn("Ponowne użycie tokenu odświeżania, rodzina unieważniona: userId={} tenantId={}",
                    token.userId(), token.tenantId());
            return null;
        }
        if (!token.isUsableAt(now)) {
            return null;
        }
        User user = users.findById(token.userId()).orElse(null);
        if (user == null || user.status() != UserStatus.ACTIVE) {
            refreshTokens.revokeFamily(token.familyId(), now);
            return null;
        }
        refreshTokens.markUsed(token.id(), now);
        return issueFor(user, token.familyId());
    }

    /** Wylogowanie: unieważnia rodzinę tokenu. Niepoprawny lub nieznany token jest ignorowany. */
    public void revoke(String raw) {
        Parsed parsed;
        try {
            parsed = parse(raw);
        } catch (UnauthorizedException e) {
            return;
        }
        try (TenantContext.Handle ignored = open(parsed)) {
            tx.executeWithoutResult(status -> refreshTokens.findByHashForUpdate(hash(raw))
                    .ifPresent(t -> refreshTokens.revokeFamily(t.familyId(), clock.instant())));
        }
    }

    /** Unieważnia wszystkie tokeny użytkownika (np. po zmianie hasła); wywołać w transakcji z kontekstem. */
    public void revokeAll(UUID userId) {
        refreshTokens.revokeAllForUser(userId, clock.instant());
    }

    private static TenantContext.Handle open(Parsed parsed) {
        return parsed.platform() ? TenantContext.openPlatformScope() : TenantContext.open(new TenantId(parsed.tenantId()));
    }

    private static Parsed parse(String raw) {
        UnauthorizedException invalid = new UnauthorizedException("invalid-refresh-token",
                "Sesja wygasła. Zaloguj się ponownie.");
        if (raw == null || raw.length() > 200) {
            throw invalid;
        }
        String[] parts = raw.split("\\.", -1);
        if (parts.length != 3 || parts[2].isEmpty()) {
            throw invalid;
        }
        try {
            UUID tenant = PLATFORM.equals(parts[0]) ? null : UUID.fromString(parts[0]);
            return new Parsed(tenant, UUID.fromString(parts[1]));
        } catch (IllegalArgumentException e) {
            throw invalid;
        }
    }

    private static String randomPart() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Pomocniczo: czy rola to administrator platformy (kontekst bez placówki). */
    public static boolean isPlatform(Role role) {
        return role == Role.PLATFORM_ADMIN;
    }
}
