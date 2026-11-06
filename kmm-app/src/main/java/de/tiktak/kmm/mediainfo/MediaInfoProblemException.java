package de.tiktak.kmm.mediainfo;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.media.MediaProbeException;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * ffprobe could not read the technical details, as a problem detail for the user interface.
 */
public class MediaInfoProblemException extends LocalizedProblemException {

    MediaInfoProblemException(MediaProbeException cause) {
        this("mediaInfo.unreadable", cause.localizedText(), cause);
    }

    private MediaInfoProblemException(String key, LocalizedText detail, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, LocalizedText.of(key + ".title"), detail, cause);
    }

    static MediaInfoProblemException seasonWithoutEpisodes(String showTitle, int season) {
        return new MediaInfoProblemException("mediaInfo.seasonWithoutEpisodes",
                LocalizedText.of("mediaInfo.seasonWithoutEpisodes.detail")
                        .with("show", showTitle)
                        .with("season", season), null);
    }
}
