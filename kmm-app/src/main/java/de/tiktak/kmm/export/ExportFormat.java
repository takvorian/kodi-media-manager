package de.tiktak.kmm.export;

import org.springframework.http.MediaType;

/**
 * Formats of the export.
 */
public enum ExportFormat {
    /** Table for spreadsheets; separated the way Excel expects it in the export language. */
    CSV("csv", MediaType.parseMediaType("text/csv; charset=UTF-8")),
    JSON("json", MediaType.APPLICATION_JSON),
    /** HTML list with downsized posters, as ZIP so it works without KMM. */
    HTML("zip", MediaType.parseMediaType("application/zip"));

    private final String extension;
    private final MediaType mediaType;

    ExportFormat(String extension, MediaType mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String extension() {
        return extension;
    }

    public MediaType mediaType() {
        return mediaType;
    }
}
