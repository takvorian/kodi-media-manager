package de.tiktak.kmm.movie;

/**
 * Cleans texts from NFOs and third-party databases before they are saved.
 */
final class Texts {

    private Texts() {
    }

    /**
     * @return the trimmed text, shortened to {@code maxLength} characters, or {@code null} if it is empty
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
