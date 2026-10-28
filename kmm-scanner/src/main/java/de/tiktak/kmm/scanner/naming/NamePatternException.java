package de.tiktak.kmm.scanner.naming;

/**
 * Ein ungültiges Namensmuster; die Meldung nennt den Fehler und die Stelle und eignet sich für die
 * Oberfläche.
 */
public class NamePatternException extends IllegalArgumentException {

    NamePatternException(String message) {
        super(message);
    }
}
