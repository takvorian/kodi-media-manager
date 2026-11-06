package de.tiktak.kmm.migration;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.tiktak.kmm.migration.ember.library.EmberMovie;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberSetLink;
import de.tiktak.kmm.migration.ember.library.EmberMovieSet;
import de.tiktak.kmm.movie.MovieImport;
import de.tiktak.kmm.movie.MovieMetadata;
import de.tiktak.kmm.movie.MovieSupplement;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.StackedFiles;

/**
 * Translates an Ember movie into a KMM import.
 */
final class EmberMovieMapper {

    static final String DVD_MARKER_FILE = "VIDEO_TS.IFO";
    static final String BLURAY_MARKER_FILE = "index.bdmv";
    private static final String IMDB = "imdb";
    private static final String TMDB = "tmdb";
    private static final String TMDB_COLLECTION = "tmdbcol";
    private static final char EXTENSION_SEPARATOR = '.';

    private EmberMovieMapper() {
    }

    /**
     * @param filePath path of the movie file on the target system
     * @param sets     movie sets from Ember by ID
     * @param zone     time zone Ember ran in; Ember stores points in time as local time
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
                EmberMappings.toInstant(movie.dateAdded(), zone),
                EmberStreamMapper.toStreamDetails(movie.streams(), filePath),
                movie.trailer(),
                EmberMappings.language(movie.language()));
    }

    /** Kodi does not know movies in several sets; the first one is imported. */
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
                EmberMappings.certification(movie.mpaa()),
                movie.genres(),
                movie.uniqueIds().get(IMDB),
                movie.uniqueIds().get(TMDB), movie.edition(), movie.videoSource())
                .withinLimits();
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
                        .filter(EmberMappings::isRated)
                        .map(rating -> new MovieSupplement.Rating(rating.source(), rating.value(), rating.max(),
                                rating.votes(), rating.defaultRating()))
                        .toList(),
                set,
                EmberMappings.userRating(movie.userRating()),
                movie.playCount(),
                EmberMappings.toInstant(movie.lastPlayed(), zone));
    }

    private static Optional<SetAssignment> set(EmberMovie movie, Map<Long, EmberMovieSet> sets) {
        return movie.sets().stream()
                .map(link -> SetAssignment.of(link, sets.get(link.setId())))
                .filter(Objects::nonNull)
                .findFirst();
    }

    /** Without a title in Ember: file name without extension, for discs the name of the movie folder. */
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
            return new SetAssignment(new MovieSupplement.SetReference(set.title(), set.plot(), set.tmdbCollectionId()),
                    link.order(),
                    set.tmdbCollectionId());
        }
    }

    /** Name of a movie for reports. */
    static String displayName(EmberMovie movie) {
        return movie.title() != null ? movie.title() : movie.path();
    }
}
