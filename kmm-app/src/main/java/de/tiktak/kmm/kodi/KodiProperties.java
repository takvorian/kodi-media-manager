package de.tiktak.kmm.kodi;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param connectTimeout      time to wait for the connection to Kodi
 * @param requestTimeout      time to wait for an answer from Kodi
 * @param syncDelay           time to wait after a change before KMM updates Kodi; further changes to the same movie
 *                            within this time trigger only one update
 * @param scanWaitInteractive how long a button of the user interface waits at most for Kodi to finish a running scan
 * @param scanWaitBackground  how long jobs and the realtime sync wait for it at most
 * @param scanPollInterval    interval in which KMM asks meanwhile
 */
@ConfigurationProperties("kmm.kodi")
public record KodiProperties(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration requestTimeout,
        @DefaultValue("5s") Duration syncDelay,
        @DefaultValue("10s") Duration scanWaitInteractive,
        @DefaultValue("30m") Duration scanWaitBackground,
        @DefaultValue("1s") Duration scanPollInterval) {
}
