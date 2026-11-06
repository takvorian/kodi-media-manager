package de.tiktak.kmm.job;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param historySize      number of finished jobs kept for display
 * @param progressInterval minimum interval between two progress reports of a job to the user interface
 */
@ConfigurationProperties("kmm.jobs")
public record JobProperties(
        @DefaultValue("50") int historySize,
        @DefaultValue("250ms") Duration progressInterval) {
}
