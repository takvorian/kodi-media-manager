package de.tiktak.kmm.movie;

import java.time.Instant;

import de.tiktak.kmm.scanner.DiscFormat;

/**
 * Film in der Listenansicht.
 */
public record MovieSummary(
        long id,
        long sourceId,
        String title,
        Integer year,
        String filePath,
        DiscFormat discFormat,
        int partCount,
        Instant addedAt) {

    static MovieSummary from(Movie movie) {
        return new MovieSummary(movie.getId(), movie.getSourceId(), movie.getTitle(), movie.getReleaseYear(),
                movie.getFilePath(), movie.getDiscFormat(), movie.getPartCount(), movie.getAddedAt());
    }
}
