package de.tiktak.kmm.mediainfo;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.media.MediaProbeException;

/**
 * ffprobe konnte die technischen Angaben nicht lesen, als Problem Detail für die Oberfläche.
 */
public class MediaInfoProblemException extends ErrorResponseException {

    MediaInfoProblemException(MediaProbeException cause) {
        this("Technische Angaben nicht lesbar", cause.getMessage(), cause);
    }

    private MediaInfoProblemException(String title, String detail, Throwable cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, cause);
        getBody().setTitle(title);
        getBody().setDetail(detail);
    }

    static MediaInfoProblemException seasonWithoutEpisodes(String showTitle, int season) {
        return new MediaInfoProblemException("Keine Episoden",
                "„%s“ hat keine Episoden in Staffel %d.".formatted(showTitle, season), null);
    }
}
