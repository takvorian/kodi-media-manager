package de.tiktak.kmm.artwork;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param preferredLanguage Sprache, deren Bilder in der Auswahl zuerst erscheinen (ISO 639-1)
 * @param maxDownloadSize   größte Bilddatei, die heruntergeladen wird
 */
@ConfigurationProperties("kmm.artwork")
public record ArtworkProperties(
        @DefaultValue("de") String preferredLanguage,
        @DefaultValue("25MB") DataSize maxDownloadSize) {
}
