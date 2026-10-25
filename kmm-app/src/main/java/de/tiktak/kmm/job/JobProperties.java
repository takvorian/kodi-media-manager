package de.tiktak.kmm.job;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param historySize      Anzahl beendeter Jobs, die zur Anzeige aufbewahrt werden
 * @param progressInterval Mindestabstand zwischen zwei Fortschrittsmeldungen eines Jobs an die Oberfläche
 */
@ConfigurationProperties("kmm.jobs")
public record JobProperties(
        @DefaultValue("50") int historySize,
        @DefaultValue("250ms") Duration progressInterval) {
}
