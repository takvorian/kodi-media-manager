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
 * Protects the REST API, Actuator and API documentation. The user interface itself, the health check and the
 * system info stay public so that the UI can show its status even before login.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    static final String[] PUBLIC_ENDPOINTS = {"/api/v1/system/info", "/actuator/health", "/actuator/health/**"};
    static final String[] PROTECTED_ENDPOINTS = {
            "/api/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};
    /**
     * Own names, must match {@code app.config.ts}: cookies apply across all ports of an address, so an
     * {@code XSRF-TOKEN} of another app on the same machine would otherwise make all modifying requests fail.
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
