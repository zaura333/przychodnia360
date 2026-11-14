package pl.przychodnia360.identity.infrastructure;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import pl.przychodnia360.identity.api.AuthenticatedUser;
import pl.przychodnia360.identity.api.Permission;
import pl.przychodnia360.identity.api.PermissionPolicy;

/** Bean "permissions" dla {@code @PreAuthorize("@permissions.has(authentication, 'SERVICE_MANAGE')")}. */
@Component("permissions")
public class PermissionEvaluator {

    public boolean has(Authentication authentication, String permission) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }
        return PermissionPolicy.has(user, Permission.valueOf(permission));
    }
}
