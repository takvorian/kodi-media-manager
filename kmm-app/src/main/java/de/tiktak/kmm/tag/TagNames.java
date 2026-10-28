package de.tiktak.kmm.tag;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Prüft Tag-Namen: getrimmt, nicht leer und höchstens so lang wie die Spalte.
 */
final class TagNames {

    static final int MAX_LENGTH = 255;

    private TagNames() {
    }

    /**
     * @throws ErrorResponseException (422) wenn der Name leer oder zu lang ist
     */
    static String clean(String name) {
        String cleaned = name == null ? "" : name.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_LENGTH) {
            ErrorResponseException problem = new ErrorResponseException(HttpStatus.UNPROCESSABLE_CONTENT);
            problem.getBody().setTitle("Ungültiger Tag");
            problem.getBody().setDetail("Ein Tag braucht einen Namen mit höchstens %d Zeichen.".formatted(MAX_LENGTH));
            throw problem;
        }
        return cleaned;
    }
}
