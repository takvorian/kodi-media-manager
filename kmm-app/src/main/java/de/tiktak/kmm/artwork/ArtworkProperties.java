package de.tiktak.kmm.artwork;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * @param maxDownloadSize größte Bilddatei, die heruntergeladen wird
 */
@ConfigurationProperties("kmm.artwork")
public record ArtworkProperties(
        @DefaultValue("25MB") DataSize maxDownloadSize) {
}
