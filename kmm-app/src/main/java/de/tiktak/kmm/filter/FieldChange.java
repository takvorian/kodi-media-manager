package de.tiktak.kmm.filter;

import jakarta.validation.constraints.Size;

/**
 * Set ({@code value}) or clear ({@code clear}) a field in the batch edit; if both are missing: unchanged.
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

    /** Whether a value to be set fits into a shorter field, such as the certification. */
    public boolean fits(int maxLength) {
        return value == null || value.length() <= maxLength;
    }
}
