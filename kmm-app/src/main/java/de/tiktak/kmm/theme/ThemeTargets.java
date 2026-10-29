package de.tiktak.kmm.theme;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import lombok.RequiredArgsConstructor;

/**
 * Bestimmt Titel und Ordner eines Films bzw. einer Serie für die Titelmusik.
 */
@Component
@RequiredArgsConstructor
class ThemeTargets {

    private final MovieMetadataService movieService;
    private final TvShowMetadataService tvShowService;

    ThemeTarget forMovie(long movieId) {
        MovieDetails movie = movieService.findDetails(movieId);
        Path directory = CompanionFiles.of(Path.of(movie.filePath()), movie.discFormat(), movie.partCount())
                .movieDirectory();
        return new ThemeTarget(MediaType.MOVIE, movie.metadata().title(), movie.metadata().originalTitle(),
                movie.metadata().year(), directory);
    }

    ThemeTarget forShow(long showId) {
        TvShowDetails show = tvShowService.findShow(showId);
        return new ThemeTarget(MediaType.TV_SHOW, show.metadata().title(), show.metadata().originalTitle(),
                show.metadata().year(), Path.of(show.directoryPath()));
    }
}
