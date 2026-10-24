package de.tiktak.kmm.plugin;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * @param directory Verzeichnis, aus dem PF4J die Plugin-JARs lädt
 */
@Validated
@ConfigurationProperties("kmm.plugins")
public record PluginProperties(@NotNull Path directory) {
}
