package de.tiktak.kmm.movie;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.settings.KmmSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Takes over the content of an NFO into a movie. Before KMM rewrites an NFO, it reads it in if it was changed
 * outside KMM since it was last read (for example by Ember or by hand); otherwise these changes would be
 * lost.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieNfoSync {

    private final MovieNfoFiles nfoFiles;
    private final MovieSets movieSets;
    private final Clock clock;

    /**
     * @throws NfoChangedOutsideException if the changed NFO is not readable; it must then not be
     *                                    overwritten
     */
    void refreshIfChangedOutside(Movie movie) {
        Optional<NfoFileState> nfoFile = nfoFiles.find(movie.companionFiles());
        if (nfoFile.isEmpty() || movie.isNfoRead() && nfoFile.get().modifiedAt().equals(movie.getNfoModifiedAt())) {
            return;
        }
        try {
            apply(movie, nfoFile.get(), nfoFiles.read(nfoFile.get()), movie.getTitle(), movie.getReleaseYear());
            log.info("NFO of movie {} was changed outside KMM or not read yet and has been read: {}", movie.getId(),
                    nfoFile.get().file());
        } catch (NfoException e) {
            throw new NfoChangedOutsideException(nfoFile.get().file(), e);
        }
    }

    /**
     * Reads in an NFO changed outside KMM so that the movie view shows its values and does not write back
     * older ones when saving. An unreadable NFO is ignored here.
     */
    void readIfOutdated(Movie movie) {
        try {
            refreshIfChangedOutside(movie);
        } catch (NfoChangedOutsideException e) {
            log.warn("NFO of movie {} not read: {}", movie.getId(), e.getMessage());
        }
    }

    /**
     * Reads the trailer from the NFO later if KMM does not know it yet, for example for movies from a version without
     * trailer support. Without an NFO there is no trailer.
     *
     * @return whether the trailer is known afterwards; {@code false} if the NFO is not readable
     */
    boolean readTrailerIfUnknown(Movie movie) {
        if (movie.isTrailerKnown()) {
            return true;
        }
        Optional<NfoFileState> nfoFile = nfoFiles.find(movie.companionFiles());
        if (nfoFile.isEmpty()) {
            movie.changeTrailer(null);
            return true;
        }
        try {
            MovieNfoSupplement supplement = nfoFiles.read(nfoFile.get()).supplement();
            movie.changeTrailer(supplement == null ? null : supplement.trailer());
            return true;
        } catch (NfoException e) {
            log.warn("Trailer of movie {} unknown, NFO not readable: {}", movie.getId(), nfoFile.get().file(), e);
            return false;
        }
    }

    /**
     * Takes over metadata, further details, technical details and the added date of the NFO. If the details
     * did not come from the NFO so far (Ember import), what only Ember knows is kept: cast members missing from the
     * NFO, and
     * technical details or trailer if the NFO has none.
     */
    void apply(Movie movie, NfoFileState nfoFile, MovieNfo nfo, String fallbackTitle, Integer fallbackYear) {
        Known known = movie.isNfoRead() ? null : new Known(movie.supplement().actors(), movie.streamDetails(),
                movie.getTrailer());
        movie.applyMetadata(MovieNfoMapper.toMetadata(nfo, fallbackTitle, fallbackYear));
        if (KmmSettings.isMetadataLanguage(nfo.language())) {
            movie.changeMetadataLanguage(nfo.language());
        }
        applySupplement(movie, nfo.supplement(), known);
        movie.markNfoSynchronized(nfoFile.modifiedAt());
        movie.markNfoRead();
    }

    private void applySupplement(Movie movie, MovieNfoSupplement nfoSupplement, Known known) {
        if (nfoSupplement == null) {
            return;
        }
        MovieSupplement supplement = MovieNfoMapper.toSupplement(nfoSupplement, clock.getZone());
        if (known != null) {
            supplement = supplement.withActors(withMissingActors(supplement.actors(), known.actors()));
        }
        movie.applySupplement(supplement, movieSets.resolve(supplement.set()));
        if (known == null || !nfoSupplement.streams().isEmpty()) {
            movie.applyStreamDetails(nfoSupplement.streams());
        }
        boolean keepKnownTrailer = known != null && known.trailer() != null
                && MovieNfoSupplement.NO_TRAILER.equals(nfoSupplement.trailer());
        if (nfoSupplement.trailer() != null && !keepKnownTrailer) {
            movie.changeTrailer(nfoSupplement.trailer());
        }
        Instant dateAdded = MovieNfoMapper.toInstant(nfoSupplement.dateAdded(), clock.getZone());
        if (dateAdded != null) {
            movie.changeAddedAt(dateAdded);
        }
    }

    /** The cast of the NFO, followed by the known ones that are missing from the NFO. */
    private static List<MovieSupplement.Actor> withMissingActors(List<MovieSupplement.Actor> fromNfo,
            List<MovieSupplement.Actor> known) {
        Set<String> names = fromNfo.stream().map(MovieSupplement.Actor::name).collect(Collectors.toSet());
        return Stream.concat(fromNfo.stream(), known.stream().filter(actor -> !names.contains(actor.name())))
                .toList();
    }

    /** What KMM knows from Ember before the NFO is first read. */
    private record Known(List<MovieSupplement.Actor> actors, StreamDetails streams, String trailer) {
    }
}
