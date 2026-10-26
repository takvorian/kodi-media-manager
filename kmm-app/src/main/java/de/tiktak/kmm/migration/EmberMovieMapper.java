package de.tiktak.kmm.migration;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.tiktak.kmm.migration.ember.library.EmberMovie;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberRating;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberSetLink;
import de.tiktak.kmm.migration.ember.library.EmberMovieSet;
import de.tiktak.kmm.movie.MovieImport;
import de.tiktak.kmm.movie.MovieMetadata;
import de.tiktak.kmm.movie.MovieSupplement;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.StackedFiles;

/**
 * Übersetzt einen Ember-Film in einen KMM-Import.
 */
final class EmberMovieMapper {

    static final String DVD_MARKER_FILE = "VIDEO_TS.IFO";
    static final String BLURAY_MARKER_FILE = "index.bdmv";
    /** Ember speichert die deutsche Freigabe als {@code Germany:16}, KMM wie der TMDB-Scraper als {@code FSK 16}. */
    private static final String EMBER_GERMAN_CERTIFICATION = "Germany:";
    private static final String KMM_GERMAN_CERTIFICATION = "FSK ";
    private static final String IMDB = "imdb";
    private static final String TMDB = "tmdb";
    private static final String TMDB_COLLECTION = "tmdbcol";
    private static final int MAX_USER_RATING = 10;
    private static final char EXTENSION_SEPARATOR = '.';

    private EmberMovieMapper() {
    }

    /**
     * @param filePath Pfad der Filmdatei im Zielsystem
     * @param sets     Filmsets aus Ember nach ID
     * @param zone     Zeitzone, in der Ember lief; Ember speichert Zeitpunkte als lokale Uhrzeit
     */
    static MovieImport toImport(EmberMovie movie, long sourceId, String filePath, Map<Long, EmberMovieSet> sets,
            ZoneId zone) {
        DiscFormat format = discFormat(filePath);
        Optional<SetAssignment> set = set(movie, sets);
        return new MovieImport(
                sourceId,
                filePath,
                format,
                format == DiscFormat.FILE ? StackedFiles.partCount(Path.of(filePath)) : 1,
                metadata(movie, filePath, format),
                supplement(movie, set.map(SetAssignment::reference).orElse(null), zone),
                set.map(SetAssignment::order).orElse(null),
                set.map(SetAssignment::tmdbCollectionId).orElse(movie.uniqueIds().get(TMDB_COLLECTION)),
                toInstant(movie.dateAdded(), zone));
    }

    /** Filme in mehreren Sets kennt Kodi nicht; übernommen wird das erste. */
    static boolean belongsToSeveralSets(EmberMovie movie) {
        return movie.sets().size() > 1;
    }

    static DiscFormat discFormat(String filePath) {
        String fileName = Path.of(filePath).getFileName().toString();
        if (fileName.equalsIgnoreCase(DVD_MARKER_FILE)) {
            return DiscFormat.DVD;
        }
        return fileName.equalsIgnoreCase(BLURAY_MARKER_FILE) ? DiscFormat.BLURAY : DiscFormat.FILE;
    }

    private static MovieMetadata metadata(EmberMovie movie, String filePath, DiscFormat format) {
        return new MovieMetadata(
                movie.title() != null ? movie.title() : titleFromPath(filePath, format),
                movie.originalTitle(),
                movie.sortTitle(),
                movie.year(),
                movie.premiered(),
                movie.plot(),
                movie.outline(),
                movie.tagline(),
                movie.runtime(),
                certification(movie.mpaa()),
                movie.genres(),
                movie.uniqueIds().get(IMDB),
                movie.uniqueIds().get(TMDB)).withinLimits();
    }

    private static MovieSupplement supplement(EmberMovie movie, MovieSupplement.SetReference set, ZoneId zone) {
        return new MovieSupplement(
                movie.actors().stream()
                        .map(actor -> new MovieSupplement.Actor(actor.name(), actor.role(), actor.thumb()))
                        .toList(),
                movie.directors(),
                movie.writers(),
                movie.studios(),
                movie.countries(),
                movie.tags(),
                movie.ratings().stream()
                        .filter(EmberMovieMapper::isRated)
                        .map(rating -> new MovieSupplement.Rating(rating.source(), rating.value(), rating.max(),
                                rating.votes(), rating.defaultRating()))
                        .toList(),
                set,
                movie.userRating() != null && movie.userRating() <= MAX_USER_RATING ? movie.userRating() : null,
                movie.playCount(),
                toInstant(movie.lastPlayed(), zone));
    }

    private static Instant toInstant(LocalDateTime localDateTime, ZoneId zone) {
        return localDateTime == null ? null : localDateTime.atZone(zone).toInstant();
    }

    private static Optional<SetAssignment> set(EmberMovie movie, Map<Long, EmberMovieSet> sets) {
        return movie.sets().stream()
                .map(link -> SetAssignment.of(link, sets.get(link.setId())))
                .filter(Objects::nonNull)
                .findFirst();
    }

    /** Ember legt für unbekannte Bewertungen Einträge mit 0 und ohne Stimmen an. */
    private static boolean isRated(EmberRating rating) {
        return rating.value() > 0 || rating.votes() != null && rating.votes() > 0;
    }

    private static String certification(String mpaa) {
        if (mpaa != null && mpaa.startsWith(EMBER_GERMAN_CERTIFICATION)) {
            return KMM_GERMAN_CERTIFICATION + mpaa.substring(EMBER_GERMAN_CERTIFICATION.length());
        }
        return mpaa;
    }

    /** Ohne Titel in Ember: Dateiname ohne Endung, bei Discs der Name des Filmordners. */
    private static String titleFromPath(String filePath, DiscFormat format) {
        Path file = Path.of(filePath);
        if (format != DiscFormat.FILE && file.getParent() != null && file.getParent().getParent() != null) {
            return file.getParent().getParent().getFileName().toString();
        }
        String fileName = file.getFileName().toString();
        int extensionStart = fileName.lastIndexOf(EXTENSION_SEPARATOR);
        return extensionStart > 0 ? fileName.substring(0, extensionStart) : fileName;
    }

    private record SetAssignment(MovieSupplement.SetReference reference, Integer order, String tmdbCollectionId) {

        static SetAssignment of(EmberSetLink link, EmberMovieSet set) {
            if (set == null || set.title() == null) {
                return null;
            }
            return new SetAssignment(new MovieSupplement.SetReference(set.title(), set.plot()), link.order(),
                    set.tmdbCollectionId());
        }
    }

    /** Name eines Films für Berichte. */
    static String displayName(EmberMovie movie) {
        return movie.title() != null ? movie.title() : movie.path();
    }
}
