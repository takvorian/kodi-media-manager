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
 * Übernimmt den Inhalt einer NFO in einen Film. Bevor KMM eine NFO neu schreibt, liest es sie ein, wenn sie seit dem
 * letzten Einlesen außerhalb von KMM geändert wurde (etwa von Ember oder von Hand); sonst gingen diese Änderungen
 * verloren.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieNfoSync {

    private final MovieNfoFiles nfoFiles;
    private final MovieSets movieSets;
    private final Clock clock;

    /**
     * @throws NfoChangedOutsideException wenn die geänderte NFO nicht lesbar ist; dann darf sie nicht überschrieben
     *                                    werden
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
     * Liest eine außerhalb von KMM geänderte NFO ein, damit die Filmansicht ihre Werte zeigt und beim Speichern nicht
     * ältere zurückschreibt. Eine unlesbare NFO bleibt hier unberücksichtigt.
     */
    void readIfOutdated(Movie movie) {
        try {
            refreshIfChangedOutside(movie);
        } catch (NfoChangedOutsideException e) {
            log.warn("NFO of movie {} not read: {}", movie.getId(), e.getMessage());
        }
    }

    /**
     * Liest den Trailer aus der NFO nach, wenn KMM ihn noch nicht kennt, etwa bei Filmen aus einer Version ohne
     * Trailer. Ohne NFO gibt es keinen Trailer.
     *
     * @return ob der Trailer danach bekannt ist; {@code false}, wenn die NFO nicht lesbar ist
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
     * Übernimmt Metadaten, weitere Angaben, technische Angaben und Hinzugefügt-Datum der NFO. Stammen die Angaben bisher
     * nicht aus der NFO (Ember-Import), bleibt erhalten, was nur Ember kennt: Darsteller, die in der NFO fehlen, und
     * technische Angaben oder Trailer, wenn die NFO keine hat.
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

    /** Die Darsteller der NFO, dahinter die bekannten, die in der NFO fehlen. */
    private static List<MovieSupplement.Actor> withMissingActors(List<MovieSupplement.Actor> fromNfo,
            List<MovieSupplement.Actor> known) {
        Set<String> names = fromNfo.stream().map(MovieSupplement.Actor::name).collect(Collectors.toSet());
        return Stream.concat(fromNfo.stream(), known.stream().filter(actor -> !names.contains(actor.name())))
                .toList();
    }

    /** Was KMM vor dem ersten Einlesen der NFO aus Ember kennt. */
    private record Known(List<MovieSupplement.Actor> actors, StreamDetails streams, String trailer) {
    }
}
