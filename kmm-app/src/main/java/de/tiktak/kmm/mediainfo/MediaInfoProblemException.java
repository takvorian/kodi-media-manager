package de.tiktak.kmm.mediainfo;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.media.MediaProbeException;

/**
 * ffprobe konnte die technischen Angaben nicht lesen, als Problem Detail für die Oberfläche.
 */
public class MediaInfoProblemException extends ErrorResponseException {

    MediaInfoProblemException(MediaProbeException cause) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, cause);
        getBody().setTitle("Technische Angaben nicht lesbar");
        getBody().setDetail(cause.getMessage());
    }
}
