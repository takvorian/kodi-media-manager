package de.tiktak.kmm.export;

import org.springframework.http.MediaType;

/**
 * Formate des Exports.
 */
public enum ExportFormat {
    /** Tabelle für Tabellenkalkulationen, Semikolon getrennt wie bei deutschem Excel. */
    CSV("csv", MediaType.parseMediaType("text/csv; charset=UTF-8")),
    JSON("json", MediaType.APPLICATION_JSON),
    /** HTML-Liste mit verkleinerten Postern, als ZIP, damit sie ohne KMM funktioniert. */
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
