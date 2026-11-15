package pl.przychodnia360.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

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
