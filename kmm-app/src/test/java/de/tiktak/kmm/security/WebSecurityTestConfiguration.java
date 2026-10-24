package de.tiktak.kmm.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Bindet die produktive Security-Konfiguration in {@code @WebMvcTest}-Slices ein. Ob die Anmeldung
 * aktiv ist, steuert der Test über {@code kmm.security.enabled}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(SecurityProperties.class)
public class WebSecurityTestConfiguration {
}
