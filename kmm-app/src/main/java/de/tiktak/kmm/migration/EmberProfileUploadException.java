package de.tiktak.kmm.migration;

/**
 * Das hochgeladene Ember-Profil fehlt, ist unvollständig oder lässt sich gerade nicht ersetzen.
 */
class EmberProfileUploadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    EmberProfileUploadException(String message) {
        super(message);
    }
}
