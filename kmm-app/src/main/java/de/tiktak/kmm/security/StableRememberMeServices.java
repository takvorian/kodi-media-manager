package de.tiktak.kmm.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/**
 * Signs the "stay signed in" cookie with the configured password instead of the stored one. Spring re-encodes the
 * stored password of the in-memory user on the first login ({@code {noop}} to {@code {bcrypt}} with a random
 * salt), so a signature over it would no longer match after the next restart. Changing the password still
 * invalidates every cookie.
 */
class StableRememberMeServices extends TokenBasedRememberMeServices {

    private final String configuredPassword;

    StableRememberMeServices(String key, UserDetailsService userDetailsService, String configuredPassword) {
        super(key, userDetailsService);
        this.configuredPassword = configuredPassword;
    }

    @Override
    protected String retrievePassword(Authentication authentication) {
        return configuredPassword;
    }

    @Override
    protected String makeTokenSignature(long tokenExpiryTime, String username, String password,
            RememberMeTokenAlgorithm algorithm) {
        return super.makeTokenSignature(tokenExpiryTime, username, configuredPassword, algorithm);
    }
}
