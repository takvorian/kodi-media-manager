package de.tiktak.kmm.filter;

import jakarta.validation.constraints.Size;

/**
 * Ein Feld in der Stapel-Bearbeitung setzen ({@code value}) oder leeren ({@code clear}); beides fehlt: unverändert.
 */
public record FieldChange(Boolean clear, @Size(max = FieldChange.MAX_LENGTH) String value) {

    public static final FieldChange KEEP = new FieldChange(false, null);
    static final int MAX_LENGTH = 255;

    public FieldChange {
        clear = Boolean.TRUE.equals(clear);
        value = value == null || value.isBlank() ? null : value.strip();
    }

    public boolean changes() {
        return clear || value != null;
    }

    public String apply(String current) {
        return clear ? null : value != null ? value : current;
    }

    /** Ob ein zu setzender Wert in ein kürzeres Feld passt, etwa die Altersfreigabe. */
    public boolean fits(int maxLength) {
        return value == null || value.length() <= maxLength;
    }
}
