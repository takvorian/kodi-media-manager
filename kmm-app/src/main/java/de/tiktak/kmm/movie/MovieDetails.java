package de.tiktak.kmm.movie;

import java.time.Instant;
import java.util.Optional;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.scanner.DiscFormat;

/**
 * All details of a movie for the detail view.
 *
 * @param nfo      state of the NFO file
 * @param trailer  trailer link or {@code null}
 * @param language own language or {@code null}, in which case the source or settings language applies
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
        NfoInfo nfo,
        String trailer,
        String language) {

    /** Details without own language. */
    public MovieDetails(long id, long sourceId, String filePath, DiscFormat discFormat, int partCount, Instant addedAt,
            MovieMetadata metadata, MovieSupplement supplement, StreamDetails streams, NfoInfo nfo, String trailer) {
        this(id, sourceId, filePath, discFormat, partCount, addedAt, metadata, supplement, streams, nfo, trailer, null);
    }

    /** Details without trailer. */
    public MovieDetails(long id, long sourceId, String filePath, DiscFormat discFormat, int partCount, Instant addedAt,
            MovieMetadata metadata, MovieSupplement supplement, StreamDetails streams, NfoInfo nfo) {
        this(id, sourceId, filePath, discFormat, partCount, addedAt, metadata, supplement, streams, nfo, null);
    }

        static MovieDetails from(Movie movie, Optional<NfoFileState> nfoFile) {
        NfoInfo nfo = nfoFile
                .map(state -> new NfoInfo(state.file().toString(), true, state.modifiedAt()))
                .orElseGet(() -> new NfoInfo(movie.companionFiles().nfoFile().toString(), false, null));
        return new MovieDetails(movie.getId(), movie.getSourceId(), movie.getFilePath(), movie.getDiscFormat(),
                movie.getPartCount(), movie.getAddedAt(), movie.metadata(), movie.supplement(), movie.streamDetails(),
                nfo, movie.getTrailer(), movie.getMetadataLanguage());
    }

    /**
     * @param path       existing NFO, or the path where KMM would create it
     * @param exists     whether the file exists
     * @param modifiedAt last modification or {@code null}
     */
    public record NfoInfo(String path, boolean exists, Instant modifiedAt) {
    }
}
