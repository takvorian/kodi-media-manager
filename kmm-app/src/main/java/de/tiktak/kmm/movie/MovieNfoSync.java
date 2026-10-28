package de.tiktak.kmm.movie;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.MovieNfo;
import de.tiktak.kmm.nfo.MovieNfoSupplement;
import de.tiktak.kmm.nfo.NfoException;
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
        if (nfoFile.isEmpty() || nfoFile.get().modifiedAt().equals(movie.getNfoModifiedAt())) {
            return;
        }
        try {
            apply(movie, nfoFile.get(), nfoFiles.read(nfoFile.get()), movie.getTitle(), movie.getReleaseYear());
            log.info("NFO of movie {} was changed outside KMM and has been read again: {}", movie.getId(),
                    nfoFile.get().file());
        } catch (NfoException e) {
            throw new NfoChangedOutsideException(nfoFile.get().file(), e);
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

    /** Übernimmt Metadaten, weitere Angaben, technische Angaben und Hinzugefügt-Datum der NFO. */
    void apply(Movie movie, NfoFileState nfoFile, MovieNfo nfo, String fallbackTitle, Integer fallbackYear) {
        movie.applyMetadata(MovieNfoMapper.toMetadata(nfo, fallbackTitle, fallbackYear));
        applySupplement(movie, nfo.supplement());
        movie.markNfoSynchronized(nfoFile.modifiedAt());
    }

    private void applySupplement(Movie movie, MovieNfoSupplement nfoSupplement) {
        if (nfoSupplement == null) {
            return;
        }
        MovieSupplement supplement = MovieNfoMapper.toSupplement(nfoSupplement, clock.getZone());
        movie.applySupplement(supplement, movieSets.resolve(supplement.set()));
        movie.applyStreamDetails(nfoSupplement.streams());
        if (nfoSupplement.trailer() != null) {
            movie.changeTrailer(nfoSupplement.trailer());
        }
        Instant dateAdded = MovieNfoMapper.toInstant(nfoSupplement.dateAdded(), clock.getZone());
        if (dateAdded != null) {
            movie.changeAddedAt(dateAdded);
        }
    }
}
