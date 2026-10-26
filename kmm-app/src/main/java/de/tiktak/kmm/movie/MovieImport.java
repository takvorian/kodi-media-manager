package de.tiktak.kmm.movie;

import java.time.Instant;
import java.util.Objects;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.scanner.DiscFormat;

/**
 * Ein Film aus einer Migration.
 *
 * @param filePath            absoluter Pfad der Filmdatei (bei DVD/Blu-ray die IFO- bzw. index.bdmv-Datei)
 * @param supplement          weitere Angaben; das Set wird über {@link MovieSupplement#set()} zugeordnet
 * @param setOrder            Position im Set oder {@code null}
 * @param setTmdbCollectionId TMDB-Collection-ID des Sets oder {@code null}
 * @param addedAt             Aufnahme in die Bibliothek oder {@code null} für „jetzt“
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
        StreamDetails streams) {

    public MovieImport {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(discFormat, "discFormat must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(supplement, "supplement must not be null");
        streams = streams == null ? StreamDetails.EMPTY : streams;
    }
}
