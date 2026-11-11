package de.tiktak.kmm.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers the login of the user interface as JSON instead of redirecting like a classic login form.
 */
@Slf4j
@RequiredArgsConstructor
class JsonLoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    static final LocalizedText FAILED_DETAIL = LocalizedText.of("security.login.failed");
    private static final LocalizedText TITLE = LocalizedText.of("security.login.title");

    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        log.info("{} signed in", authentication.getName());
        response.setStatus(HttpStatus.OK.value());
        write(response, MediaType.APPLICATION_JSON_VALUE, AuthStatus.of(true, authentication));
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("Failed login from {}: {}", request.getRemoteAddr(), exception.getMessage());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        write(response, MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                LocalizedProblems.problem(HttpStatus.UNAUTHORIZED, TITLE, FAILED_DETAIL));
    }

    private void write(HttpServletResponse response, String contentType, Object body) throws IOException {
        response.setContentType(contentType);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
