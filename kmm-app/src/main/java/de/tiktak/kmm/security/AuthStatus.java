package de.tiktak.kmm.security;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;

/**
 * Whether the user interface may be used: with login disabled always, otherwise once signed in.
 *
 * @param username the signed-in user, {@code null} if nobody is signed in or login is disabled
 */
public record AuthStatus(boolean securityEnabled, boolean authenticated, String username) {

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    static AuthStatus of(boolean securityEnabled, Authentication authentication) {
        if (!securityEnabled) {
            return new AuthStatus(false, true, null);
        }
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !TRUST_RESOLVER.isAnonymous(authentication);
        return new AuthStatus(true, authenticated, authenticated ? authentication.getName() : null);
    }
}
