package de.tiktak.kmm;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stellt die Systemuhr als Bean bereit, damit zeitabhängiger Code in Tests eine feste Uhr nutzen kann.
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
