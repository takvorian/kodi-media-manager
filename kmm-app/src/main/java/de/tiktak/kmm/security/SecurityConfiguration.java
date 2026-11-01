package de.tiktak.kmm.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Schützt REST-API, Actuator und API-Dokumentation. Die Oberfläche selbst, der Health-Check und die
 * System-Info bleiben öffentlich, damit die UI ihren Status auch vor der Anmeldung anzeigen kann.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    static final String[] PUBLIC_ENDPOINTS = {"/api/v1/system/info", "/actuator/health", "/actuator/health/**"};
    static final String[] PROTECTED_ENDPOINTS = {
            "/api/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};
    /**
     * Eigene Namen, muss zu {@code app.config.ts} passen: Cookies gelten über alle Ports einer Adresse hinweg, ein
     * {@code XSRF-TOKEN} einer anderen App auf demselben Rechner ließe sonst alle ändernden Anfragen scheitern.
     */
    static final String CSRF_COOKIE = "KMM-XSRF-TOKEN";
    static final String CSRF_HEADER = "X-KMM-XSRF-TOKEN";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties,
            ObjectMapper objectMapper) {
        http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()));
        http.exceptionHandling(handling -> handling.accessDeniedHandler(new ProblemAccessDeniedHandler(objectMapper)));
        if (properties.enabled()) {
            http.authorizeHttpRequests(requests -> requests
                            .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                            .requestMatchers(PROTECTED_ENDPOINTS).authenticated()
                            .anyRequest().permitAll())
                    .httpBasic(withDefaults());
        } else {
            log.warn("Authentication is disabled (kmm.security.enabled=false)");
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        }
        return http.build();
    }

    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE);
        repository.setHeaderName(CSRF_HEADER);
        return repository;
    }
}
