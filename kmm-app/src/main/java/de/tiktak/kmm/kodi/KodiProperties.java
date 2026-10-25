package de.tiktak.kmm.kodi;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param connectTimeout Wartezeit auf den Verbindungsaufbau zu Kodi
 * @param requestTimeout Wartezeit auf eine Antwort von Kodi
 * @param syncDelay      Wartezeit nach einer Änderung, bevor KMM Kodi aktualisiert; weitere
 *                       Änderungen am selben Film in dieser Zeit lösen nur eine Aktualisierung aus
 */
@ConfigurationProperties("kmm.kodi")
public record KodiProperties(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("15s") Duration requestTimeout,
        @DefaultValue("5s") Duration syncDelay) {
}
