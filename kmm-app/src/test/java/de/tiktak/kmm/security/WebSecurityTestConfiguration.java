package de.tiktak.kmm.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Includes the production security configuration in {@code @WebMvcTest} slices. Whether login is
 * enabled is controlled by the test via {@code kmm.security.enabled}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(SecurityProperties.class)
public class WebSecurityTestConfiguration {
}
