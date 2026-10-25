package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Ein beim Scan gefundener Film.
 *
 * @param file      Hauptdatei: Videodatei, erster Teil eines Stapels, {@code VIDEO_TS.IFO} oder {@code index.bdmv}
 * @param title     abgeleiteter Titel
 * @param year      abgeleitetes Jahr oder {@code null}
 * @param format    Speicherform
 * @param partCount Anzahl der Teile (größer 1 bei {@code cd1}/{@code cd2} usw.)
 */
public record MovieCandidate(Path file, String title, Integer year, DiscFormat format, int partCount) {

    public MovieCandidate {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(format, "format must not be null");
        if (partCount < 1) {
            throw new IllegalArgumentException("partCount must be at least 1");
        }
    }
}
