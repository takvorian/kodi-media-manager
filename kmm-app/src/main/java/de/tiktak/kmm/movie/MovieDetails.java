package de.tiktak.kmm.movie;

import java.time.Instant;
import java.util.Optional;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.scanner.DiscFormat;

/**
 * Alle Angaben zu einem Film für die Detailansicht.
 *
 * @param nfo Zustand der NFO-Datei
 */
public record MovieDetails(
        long id,
        long sourceId,
        String filePath,
        DiscFormat discFormat,
        int partCount,
        Instant addedAt,
        MovieMetadata metadata,
        MovieSupplement supplement,
        StreamDetails streams,
        NfoInfo nfo) {

    static MovieDetails from(Movie movie, Optional<NfoFileState> nfoFile) {
        NfoInfo nfo = nfoFile
                .map(state -> new NfoInfo(state.file().toString(), true, state.modifiedAt()))
                .orElseGet(() -> new NfoInfo(movie.companionFiles().nfoFile().toString(), false, null));
        return new MovieDetails(movie.getId(), movie.getSourceId(), movie.getFilePath(), movie.getDiscFormat(),
                movie.getPartCount(), movie.getAddedAt(), movie.metadata(), movie.supplement(), movie.streamDetails(),
                nfo);
    }

    /**
     * @param path       vorhandene NFO bzw. Pfad, unter dem KMM sie anlegen würde
     * @param exists     ob die Datei existiert
     * @param modifiedAt letzte Änderung oder {@code null}
     */
    public record NfoInfo(String path, boolean exists, Instant modifiedAt) {
    }
}
