package de.tiktak.kmm.kodi.rpc;

/**
 * Kodi ist nicht erreichbar, lehnt die Anmeldung ab oder meldet einen Fehler. Die Meldung ist für
 * die Oberfläche bestimmt.
 */
public class KodiException extends RuntimeException {

    KodiException(String message) {
        super(message);
    }

    KodiException(String message, Throwable cause) {
        super(message, cause);
    }
}
