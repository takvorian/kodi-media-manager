package de.tiktak.kmm.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Beantwortet abgelehnte Anfragen wie die übrige API als Problem mit verständlichem Text und hält sie
 * im Log fest. Ohne das sähe man in der Oberfläche nur „Unknown Error“ und im Log gar nichts.
 */
@Slf4j
@RequiredArgsConstructor
class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    static final String CSRF_DETAIL = "Der Server hat die Anfrage abgelehnt, weil das Sicherheits-Cookie fehlt oder "
            + "nicht passt. Lade die Seite neu. Hilft das nicht, lösche die Cookies dieser Adresse im Browser.";
    static final String DENIED_DETAIL = "Für diese Aktion fehlt die Berechtigung.";
    private static final String TITLE = "Anfrage abgelehnt";

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        log.warn("Rejected {} {}: {}", request.getMethod(), request.getRequestURI(), exception.getMessage());
        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                exception instanceof CsrfException ? CSRF_DETAIL : DENIED_DETAIL);
        problem.setTitle(TITLE);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
