package de.tiktak.kmm.security;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import lombok.extern.slf4j.Slf4j;

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

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties) {
        http.csrf(csrf -> csrf.spa());
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
}
