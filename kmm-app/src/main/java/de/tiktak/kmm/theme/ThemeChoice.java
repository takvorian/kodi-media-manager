package de.tiktak.kmm.theme;

import java.net.URI;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Die gewählte Titelmusik, wie sie ein {@link ThemeOption} beschreibt.
 */
public record ThemeChoice(
        @NotBlank String scraperId,
        @NotNull URI source,
        @NotNull URI webUrl,
        String name,
        String subject,
        URI previewUrl,
        Integer durationSeconds) {

    ThemeOption toOption() {
        return new ThemeOption(name, subject, source, previewUrl, webUrl, durationSeconds, scraperId, null, false);
    }
}
