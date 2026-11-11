package de.tiktak.kmm.security;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Tells the user interface whether it has to show the login page. Signing in and out is handled by the security
 * filters under {@link #LOGIN_PATH} and {@link #LOGOUT_PATH}.
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    public static final String BASE_PATH = "/api/v1/auth";
    public static final String ME_PATH = BASE_PATH + "/me";
    public static final String LOGIN_PATH = BASE_PATH + "/login";
    public static final String LOGOUT_PATH = BASE_PATH + "/logout";

    private final SecurityProperties properties;

    @GetMapping(ME_PATH)
    public AuthStatus me(Authentication authentication) {
        return AuthStatus.of(properties.enabled(), authentication);
    }
}
