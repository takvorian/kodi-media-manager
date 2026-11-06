package de.tiktak.kmm.movie;

import java.nio.file.Path;

import de.tiktak.kmm.scanner.DiscFormat;

/**
 * The video file of a movie, as ffprobe needs it.
 *
 * @param file              main file; the first part for multi-part movies
 * @param hasStreamDetails  whether technical details are known
 */
public record MovieMedia(long movieId, String title, Path file, DiscFormat format, boolean hasStreamDetails) {

    static MovieMedia of(Movie movie) {
        return new MovieMedia(movie.getId(), movie.getTitle(), Path.of(movie.getFilePath()), movie.getDiscFormat(),
                !movie.streamDetails().isEmpty());
    }
}
