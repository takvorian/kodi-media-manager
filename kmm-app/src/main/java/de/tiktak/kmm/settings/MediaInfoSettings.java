package de.tiktak.kmm.settings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Settings for the technical details (stream details) that KMM reads with ffprobe.
 *
 * @param ffprobePath   path to ffprobe or just {@code ffprobe} if it is on the PATH
 * @param probeOnScrape whether to read after automatic scraping, like Ember's "MetaDataScan"
 * @param probeNewFiles whether the scan reads new files whose NFO contains no technical details
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
