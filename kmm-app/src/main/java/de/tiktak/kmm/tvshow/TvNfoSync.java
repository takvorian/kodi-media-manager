package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.TvShowNfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Übernimmt den Inhalt von Serien- und Episoden-NFOs. Bevor KMM eine NFO neu schreibt, liest es sie ein, wenn sie
 * seit dem letzten Einlesen außerhalb von KMM geändert wurde; sonst gingen diese Änderungen verloren. Eine
 * {@code tvshow.nfo} liest es auch ein, wenn die Angaben der Serie nicht aus ihr stammen (Ember-Import).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TvNfoSync {

    private final TvShowNfoFiles nfoFiles;
    private final Clock clock;

    /**
     * @throws TvShowProblemException wenn die geänderte NFO nicht lesbar ist; dann darf sie nicht überschrieben werden
     */
    void refreshShowIfChangedOutside(TvShow show) {
        Optional<TvNfoState> nfo = nfoFiles.findShowNfo(show.directory());
        if (nfo.isEmpty() || show.isNfoRead() && nfo.get().modifiedAt().equals(show.getNfoModifiedAt())) {
            return;
        }
        TvShowMetadata current = show.metadata();
        applyShow(show, read(nfo.get(), nfoFiles::readShow), current.title(), current.year());
        show.markNfoSynchronized(nfo.get().modifiedAt());
        log.info("NFO of TV show {} was changed outside KMM or not read yet and has been read: {}", show.getId(),
                nfo.get().file());
    }

    /**
     * Für alle Episoden einer Datei, die sich eine NFO teilen.
     *
     * @throws TvShowProblemException wenn die geänderte NFO nicht lesbar ist
     */
    void refreshEpisodesIfChangedOutside(List<Episode> sameFile) {
        Optional<TvNfoState> nfo = nfoFiles.findEpisodeNfo(sameFile.getFirst().file());
        if (nfo.isEmpty() || sameFile.stream().allMatch(episode -> episode.isNfoRead()
                && nfo.get().modifiedAt().equals(episode.getNfoModifiedAt()))) {
            return;
        }
        List<EpisodeNfo> episodes = read(nfo.get(), nfoFiles::readEpisodes);
        for (Episode episode : sameFile) {
            matching(episode, episodes, sameFile.size() == 1).ifPresent(match -> applyEpisode(episode, match));
            episode.markNfoSynchronized(nfo.get().modifiedAt());
            episode.markNfoRead();
        }
        log.info("NFO {} was changed outside KMM or not read yet and has been read", nfo.get().file());
    }

    static void applyShow(TvShow show, TvShowNfo nfo, String fallbackTitle, Integer fallbackYear) {
        show.applyMetadata(TvNfoMapper.toMetadata(nfo, fallbackTitle, fallbackYear));
        show.applyExtras(TvNfoMapper.toExtras(nfo));
        if (nfo.trailer() != null) {
            show.changeTrailer(nfo.trailer());
        }
        show.markNfoRead();
    }

    /**
     * Liest den Trailer aus der NFO nach, wenn KMM ihn noch nicht kennt, etwa bei Serien aus einer Version ohne
     * Trailer. Ohne NFO gibt es keinen Trailer.
     *
     * @return ob der Trailer danach bekannt ist; {@code false}, wenn die NFO nicht lesbar ist
     */
    boolean readTrailerIfUnknown(TvShow show) {
        if (show.isTrailerKnown()) {
            return true;
        }
        Optional<TvNfoState> nfo = nfoFiles.findShowNfo(show.directory());
        if (nfo.isEmpty()) {
            show.changeTrailer(null);
            return true;
        }
        try {
            show.changeTrailer(nfoFiles.readShow(nfo.get()).trailer());
            return true;
        } catch (NfoException e) {
            log.warn("Trailer of TV show {} unknown, NFO not readable: {}", show.getId(), nfo.get().file(), e);
            return false;
        }
    }

    /** Die Episode in der NFO; eine Datei mit nur einer Episode darf auch eine anders nummerierte enthalten. */
    static Optional<EpisodeNfo> matching(Episode episode, List<EpisodeNfo> episodes, boolean singleEpisodeFile) {
        return episodes.stream()
                .filter(nfo -> nfo.describes(episode.getSeason(), episode.getEpisode()))
                .findFirst()
                .or(() -> singleEpisodeFile && episodes.size() == 1 ? Optional.of(episodes.getFirst())
                        : Optional.empty());
    }

    /**
     * Übernimmt die NFO. Stammen die Angaben bisher nicht aus der NFO (Ember-Import) und hat sie keine technischen
     * Angaben, bleiben die bekannten erhalten.
     */
    void applyEpisode(Episode episode, EpisodeNfo nfo) {
        EpisodeExtras extras = TvNfoMapper.toExtras(nfo, clock.getZone());
        if (!episode.isNfoRead() && extras.streams().isEmpty()) {
            extras = extras.withStreams(episode.extras().streams());
        }
        episode.applyMetadata(TvNfoMapper.toMetadata(nfo, episode.getTitle()));
        episode.applyExtras(extras);
        episode.markNfoRead();
        Instant dateAdded = TvNfoMapper.toInstant(nfo.dateAdded(), clock.getZone());
        if (dateAdded != null) {
            episode.changeAddedAt(dateAdded);
        }
    }

    private static <T> T read(TvNfoState nfo, Function<TvNfoState, T> reader) {
        try {
            return reader.apply(nfo);
        } catch (NfoException e) {
            throw TvShowProblemException.nfoChangedOutside(nfo.file(), e);
        }
    }
}
