package de.tiktak.kmm.movie;

import java.nio.file.Path;

import de.tiktak.kmm.scanner.DiscFormat;

/**
 * Die Videodatei eines Films, wie ffprobe sie braucht.
 *
 * @param file              Hauptdatei, bei mehrteiligen Filmen der erste Teil
 * @param hasStreamDetails  ob technische Angaben bekannt sind
 */
public record MovieMedia(long movieId, String title, Path file, DiscFormat format, boolean hasStreamDetails) {

    static MovieMedia of(Movie movie) {
        return new MovieMedia(movie.getId(), movie.getTitle(), Path.of(movie.getFilePath()), movie.getDiscFormat(),
                !movie.streamDetails().isEmpty());
    }
}
