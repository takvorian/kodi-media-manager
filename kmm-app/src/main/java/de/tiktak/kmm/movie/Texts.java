package de.tiktak.kmm.movie;

/**
 * Bereinigt Texte aus NFOs und Fremddatenbanken, bevor sie gespeichert werden.
 */
final class Texts {

    private Texts() {
    }

    /**
     * @return den getrimmten, auf {@code maxLength} Zeichen gekürzten Text oder {@code null}, wenn er leer ist
     */
    static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        return stripped.length() > maxLength ? stripped.substring(0, maxLength).strip() : stripped;
    }
}
