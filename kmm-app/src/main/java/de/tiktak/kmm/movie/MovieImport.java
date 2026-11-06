package de.tiktak.kmm.movie;

import java.time.Instant;
import java.util.Objects;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.scanner.DiscFormat;

/**
 * A movie from a migration.
 *
 * @param filePath            absolute path of the movie file (for DVD/Blu-ray the IFO or index.bdmv file)
 * @param supplement          further details; the set is assigned via {@link MovieSupplement#set()}
 * @param setOrder            position in the set or {@code null}
 * @param setTmdbCollectionId TMDB collection ID of the set or {@code null}
 * @param addedAt             addition to the library or {@code null} for "now"
 * @param trailer             trailer link or {@code null}
 * @param language            language or {@code null}, in which case an existing one is kept
 */
public record MovieImport(
        long sourceId,
        String filePath,
        DiscFormat discFormat,
        int partCount,
        MovieMetadata metadata,
        MovieSupplement supplement,
        Integer setOrder,
        String setTmdbCollectionId,
        Instant addedAt,
        StreamDetails streams,
        String trailer,
        String language) {

    public MovieImport {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(discFormat, "discFormat must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(supplement, "supplement must not be null");
        streams = streams == null ? StreamDetails.EMPTY : streams;
    }

    /** A movie without a language. */
    public MovieImport(long sourceId, String filePath, DiscFormat discFormat, int partCount, MovieMetadata metadata,
            MovieSupplement supplement, Integer setOrder, String setTmdbCollectionId, Instant addedAt,
            StreamDetails streams, String trailer) {
        this(sourceId, filePath, discFormat, partCount, metadata, supplement, setOrder, setTmdbCollectionId, addedAt,
                streams, trailer, null);
    }

    /** A movie without a trailer value. */
    public MovieImport(long sourceId, String filePath, DiscFormat discFormat, int partCount, MovieMetadata metadata,
            MovieSupplement supplement, Integer setOrder, String setTmdbCollectionId, Instant addedAt,
            StreamDetails streams) {
        this(sourceId, filePath, discFormat, partCount, metadata, supplement, setOrder, setTmdbCollectionId, addedAt,
                streams, null);
    }
}
