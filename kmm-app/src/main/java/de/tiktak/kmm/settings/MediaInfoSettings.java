package de.tiktak.kmm.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Einstellungen für die technischen Angaben (Stream-Details), die KMM mit ffprobe ausliest.
 *
 * @param ffprobePath   Pfad zu ffprobe oder nur {@code ffprobe}, wenn es auf dem PATH liegt
 * @param probeOnScrape ob nach dem automatischen Scrapen ausgelesen wird, wie Embers „MetaDataScan“
 * @param probeNewFiles ob der Scan neue Dateien ausliest, deren NFO keine technischen Angaben enthält
 */
public record MediaInfoSettings(
        @NotBlank @Size(max = KmmSettings.MAX_PATH_LENGTH) String ffprobePath,
        boolean probeOnScrape,
        boolean probeNewFiles) {

    public static final MediaInfoSettings DEFAULTS = new MediaInfoSettings("ffprobe", true, true);

    public MediaInfoSettings {
        ffprobePath = ffprobePath == null ? null : ffprobePath.strip();
    }
}
