package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Ein beim Scan gefundener Film.
 *
 * @param file      Hauptdatei: Videodatei, erster Teil eines Stapels, {@code VIDEO_TS.IFO} oder {@code index.bdmv}
 * @param title     Titel: der unveränderte Name des Verzeichnisses, wenn der Film allein darin liegt
 * @param year      abgeleitetes Jahr oder {@code null}
 * @param imdbId    abgeleitete IMDb-ID oder {@code null}
 * @param format    Speicherform
 * @param partCount Anzahl der Teile (größer 1 bei {@code cd1}/{@code cd2} usw.)
 * @param fileTitle Titel laut Dateiname; so hießen bereits gescannte Filme bisher
 */
public record MovieCandidate(Path file, String title, Integer year, String imdbId, DiscFormat format, int partCount,
        String fileTitle) {

    public MovieCandidate {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(format, "format must not be null");
        if (partCount < 1) {
            throw new IllegalArgumentException("partCount must be at least 1");
        }
    }

    /** Ein Film, dessen Titel aus dem Dateinamen stammt. */
    public MovieCandidate(Path file, String title, Integer year, String imdbId, DiscFormat format, int partCount) {
        this(file, title, year, imdbId, format, partCount, title);
    }

    /**
     * Übernimmt den Namen des Verzeichnisses als Titel und ergänzt daraus Jahr und ID; bekannte Werte bleiben. Bei DVD
     * und Blu-ray ist das Verzeichnis {@code VIDEO_TS} bzw. {@code BDMV}; dort steht der Titel schon fest.
     *
     * @param name der aus dem Verzeichnisnamen abgeleitete Name
     */
    MovieCandidate withDirectoryName(String directoryName, ParsedMovieName name) {
        return new MovieCandidate(file, format == DiscFormat.FILE ? directoryName : title,
                year != null ? year : name.year(), imdbId != null ? imdbId : name.imdbId(), format, partCount,
                fileTitle);
    }
}
