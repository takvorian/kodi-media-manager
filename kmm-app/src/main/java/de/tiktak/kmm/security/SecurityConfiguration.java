package de.tiktak.kmm.security;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Protects the REST API, Actuator and API documentation. The user interface itself, the health check, the system
 * info and the login status stay public so that the UI can show its own login page. A 401 carries no
 * {@code WWW-Authenticate}, so the browser never opens its own login dialog; scripts may still send HTTP Basic
 * credentials with every request.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/system/info", AuthController.ME_PATH, "/actuator/health", "/actuator/health/**"};
    static final String[] PROTECTED_ENDPOINTS = {
            "/api/**", "/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};
    /**
     * Own names, must match {@code app.config.ts}: cookies apply across all ports of an address, so an
     * {@code XSRF-TOKEN} of another app on the same machine would otherwise make all modifying requests fail.
     */
    static final String CSRF_COOKIE = "KMM-XSRF-TOKEN";
    static final String CSRF_HEADER = "X-KMM-XSRF-TOKEN";
    /** Own name for the same reason as the CSRF cookie. */
    static final String REMEMBER_ME_COOKIE = "KMM-REMEMBER-ME";
    static final String REMEMBER_ME_PARAMETER = "rememberMe";
    static final Duration REMEMBER_ME_VALIDITY = Duration.ofDays(30);
    /** Route of the login page in the user interface. */
    private static final String LOGIN_PAGE = "/login";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties,
            ObjectMapper objectMapper, UserDetailsService userDetailsService,
            org.springframework.boot.security.autoconfigure.SecurityProperties springSecurity,
            @Value("${kmm.home}") Path home) {
        http.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()));
        http.exceptionHandling(handling -> handling.accessDeniedHandler(new ProblemAccessDeniedHandler(objectMapper)));
        if (properties.enabled()) {
            http.authorizeHttpRequests(requests -> requests
                            .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                            .requestMatchers(PROTECTED_ENDPOINTS).authenticated()
                            .anyRequest().permitAll())
                    .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized()))
                    .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthorized()))
                    .requestCache(cache -> cache.disable());
            JsonLoginHandlers loginHandlers = new JsonLoginHandlers(objectMapper);
            String rememberMeKey = RememberMeKey.readOrCreate(home);
            http.formLogin(login -> login
                            .loginPage(LOGIN_PAGE)
                            .loginProcessingUrl(AuthController.LOGIN_PATH)
                            .successHandler(loginHandlers)
                            .failureHandler(loginHandlers))
                    .rememberMe(rememberMe -> rememberMe
                            .key(rememberMeKey)
                            .rememberMeServices(rememberMeServices(rememberMeKey, userDetailsService,
                                    springSecurity.getUser().getPassword())))
                    .logout(logout -> logout
                            .logoutUrl(AuthController.LOGOUT_PATH)
                            .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler()));
        } else {
            log.warn("Authentication is disabled (kmm.security.enabled=false)");
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
        }
        return http.build();
    }

    private static StableRememberMeServices rememberMeServices(String key, UserDetailsService userDetailsService,
            String password) {
        StableRememberMeServices services = new StableRememberMeServices(key, userDetailsService, password);
        services.setCookieName(REMEMBER_ME_COOKIE);
        services.setParameter(REMEMBER_ME_PARAMETER);
        services.setTokenValiditySeconds((int) REMEMBER_ME_VALIDITY.toSeconds());
        return services;
    }

    private static HttpStatusEntryPoint unauthorized() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    }

    private static CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE);
        repository.setHeaderName(CSRF_HEADER);
        return repository;
    }
}
