package de.tiktak.kmm.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers rejected requests like the rest of the API as a problem with an understandable text and records them in
 * the log. Without it the user interface would only show "Unknown Error" and the log nothing at all.
 */
@Slf4j
@RequiredArgsConstructor
class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    static final LocalizedText CSRF_DETAIL = LocalizedText.of("security.denied.csrf");
    static final LocalizedText DENIED_DETAIL = LocalizedText.of("security.denied.permission");
    private static final LocalizedText TITLE = LocalizedText.of("security.denied.title");

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        log.warn("Rejected {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problem = LocalizedProblems.problem(HttpStatus.FORBIDDEN, TITLE,
                exception instanceof CsrfException ? CSRF_DETAIL : DENIED_DETAIL);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
