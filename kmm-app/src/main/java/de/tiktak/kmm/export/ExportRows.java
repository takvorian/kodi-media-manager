package de.tiktak.kmm.export;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.artwork.ArtworkService;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.artwork.TvShowArtworkService;
import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.movie.MovieSupplement;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.nfo.StreamLabels;
import de.tiktak.kmm.tvshow.EpisodeSummary;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import lombok.RequiredArgsConstructor;

/**
 * Builds the export tables for movies and TV shows from the library, labelled in the requested language.
 */
@Component
@RequiredArgsConstructor
class ExportRows {

    /** Keys of the movie columns: name in JSON and, after {@link #COLUMN_KEY_PREFIX}, key of the heading. */
    static final List<String> MOVIE_COLUMNS = List.of("title", "originalTitle", "year", "genres", "runtime", "mpaa",
            "rating", "userRating", "watched", "resolution", "videoCodec", "audioLanguages", "subtitleLanguages", "set",
            "tags", "imdbId", "tmdbId", "file", "added");
    static final List<String> SHOW_COLUMNS = List.of("title", "originalTitle", "year", "status", "genres", "studios",
            "seasons", "episodes", "unwatched", "rating", "userRating", "tags", "tvdbId", "tmdbId", "imdbId",
            "directory", "added");
    static final String COLUMN_KEY_PREFIX = "export.column.";
    private static final LocalizedText MOVIES_TITLE = LocalizedText.of("export.table.movies");
    private static final LocalizedText SHOWS_TITLE = LocalizedText.of("export.table.tvShows");

    private final MovieMetadataService movieService;
    private final TvShowMetadataService showService;
    private final ArtworkService movieArtwork;
    private final TvShowArtworkService showArtwork;

    /** @param withPosters whether to look up the poster files (only needed for the HTML list) */
    ExportTable movies(List<Long> movieIds, boolean withPosters, ZoneId zone, Locale locale) {
        List<ExportTable.Row> rows = new ArrayList<>();
        for (long movieId : movieIds) {
            MovieDetails movie = movieService.findDetails(movieId);
            rows.add(new ExportTable.Row(movieId, movieValues(movie, zone),
                    withPosters ? movieArtwork.posterFile(movie).orElse(null) : null));
        }
        rows.sort(Comparator.comparing(row -> String.valueOf(row.values().getFirst()), String.CASE_INSENSITIVE_ORDER));
        return new ExportTable(Messages.render(MOVIES_TITLE, locale), locale, columns(MOVIE_COLUMNS, locale), rows);
    }

    ExportTable shows(List<Long> showIds, boolean withPosters, ZoneId zone, Locale locale) {
        List<ExportTable.Row> rows = new ArrayList<>();
        for (long showId : showIds) {
            TvShowDetails show = showService.findShow(showId);
            rows.add(new ExportTable.Row(showId, showValues(show, zone),
                    withPosters ? showArtwork.posterFile(show).orElse(null) : null));
        }
        rows.sort(Comparator.comparing(row -> String.valueOf(row.values().getFirst()), String.CASE_INSENSITIVE_ORDER));
        return new ExportTable(Messages.render(SHOWS_TITLE, locale), locale, columns(SHOW_COLUMNS, locale), rows);
    }

    static List<Object> movieValues(MovieDetails movie, ZoneId zone) {
        var metadata = movie.metadata();
        MovieSupplement supplement = movie.supplement();
        StreamDetails streams = movie.streams() == null ? StreamDetails.EMPTY : movie.streams();
        Optional<StreamDetails.Video> video = streams.videos().stream().findFirst();
        return values(metadata.title(), metadata.originalTitle(), metadata.year(), metadata.genres(),
                metadata.runtime(), metadata.mpaa(),
                rating(supplement.ratings().stream().map(r -> new Rating(r.value(), r.max(), r.defaultRating()))),
                supplement.userRating(), supplement.playCount() != null && supplement.playCount() > 0,
                video.flatMap(StreamLabels::resolution).orElse(null), video.map(StreamDetails.Video::codec).orElse(null),
                languages(streams.audios().stream().map(StreamDetails.Audio::language)),
                languages(streams.subtitles().stream().map(StreamDetails.Subtitle::language)),
                supplement.set() == null ? null : supplement.set().name(), supplement.tags(), metadata.imdbId(),
                metadata.tmdbId(), movie.filePath(), date(movie.addedAt(), zone));
    }

    static List<Object> showValues(TvShowDetails show, ZoneId zone) {
        var metadata = show.metadata();
        List<EpisodeSummary> episodes = show.episodes();
        long unwatched = episodes.stream().filter(e -> e.playCount() == null || e.playCount() == 0).count();
        long seasons = show.seasons().stream().filter(season -> season.episodeCount() > 0).count();
        return values(metadata.title(), metadata.originalTitle(), metadata.year(), metadata.status(),
                metadata.genres(), metadata.studios(), seasons, episodes.size(), unwatched,
                rating(show.extras().ratings().stream().map(r -> new Rating(r.value(), r.max(), r.defaultRating()))),
                metadata.userRating(), metadata.tags(), metadata.tvdbId(), metadata.tmdbId(), metadata.imdbId(),
                show.directoryPath(), date(show.addedAt(), zone));
    }

    /** The default rating, otherwise the first one, with one decimal and based on 10. */
    private static Double rating(Stream<Rating> ratings) {
        List<Rating> all = ratings.toList();
        return all.stream().filter(Rating::defaultRating).findFirst().or(() -> all.stream().findFirst())
                .map(rating -> Math.round(rating.value() * 10.0 / (rating.max() == null ? 10 : rating.max()) * 10)
                        / 10.0)
                .orElse(null);
    }

    private static List<String> languages(Stream<String> languages) {
        return List.copyOf(languages.filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private static LocalDate date(Instant instant, ZoneId zone) {
        return instant == null ? null : LocalDate.ofInstant(instant, zone);
    }

    private static List<Object> values(Object... values) {
        return Arrays.asList(values);
    }

    private static List<ExportTable.Column> columns(List<String> keys, Locale locale) {
        return keys.stream()
                .map(key -> new ExportTable.Column(key,
                        Messages.render(LocalizedText.of(COLUMN_KEY_PREFIX + key), locale)))
                .toList();
    }

    private record Rating(double value, Integer max, boolean defaultRating) {
    }
}
