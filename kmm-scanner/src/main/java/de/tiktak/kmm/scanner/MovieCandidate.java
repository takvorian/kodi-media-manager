package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A movie found during a scan.
 *
 * @param file      main file: video file, first part of a stack, {@code VIDEO_TS.IFO} or {@code index.bdmv}
 * @param title     title: the unchanged name of the directory if the movie is the only one in it
 * @param year      derived year or {@code null}
 * @param imdbId    derived IMDb ID or {@code null}
 * @param format    storage format
 * @param partCount number of parts (greater than 1 for {@code cd1}/{@code cd2} etc.)
 * @param fileTitle title according to the file name; already scanned movies used to be called that
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

    /** A movie whose title comes from the file name. */
    public MovieCandidate(Path file, String title, Integer year, String imdbId, DiscFormat format, int partCount) {
        this(file, title, year, imdbId, format, partCount, title);
    }

    /**
     * Takes the name of the directory as title and adds year and ID from it; known values are kept. For DVD and
     * Blu-ray the directory is {@code VIDEO_TS} or {@code BDMV}; there the title is already settled.
     *
     * @param name the name derived from the directory name
     */
    MovieCandidate withDirectoryName(String directoryName, ParsedMovieName name) {
        return new MovieCandidate(file, format == DiscFormat.FILE ? directoryName : title,
                year != null ? year : name.year(), imdbId != null ? imdbId : name.imdbId(), format, partCount,
                fileTitle);
    }
}
