package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.TvShowNfo;
import de.tiktak.kmm.settings.KmmSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Takes over the content of TV show and episode NFOs. Before KMM rewrites an NFO, it reads it if it
 * was changed outside KMM since it was last read; otherwise these changes would be lost. It also reads a
 * {@code tvshow.nfo} if the details of the TV show do not come from it (Ember import).
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TvNfoSync {

    private final TvShowNfoFiles nfoFiles;
    private final Clock clock;

    /**
     * @throws TvShowProblemException if the changed NFO is unreadable; it must then not be overwritten
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
     * For all episodes of a file that share an NFO.
     *
     * @throws TvShowProblemException if the changed NFO is unreadable
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

    /**
     * Takes over the {@code tvshow.nfo}. If the details so far do not come from the NFO (Ember import), actors
     * that exist only in Ember's data are kept.
     */
    /**
     * Reads the {@code tvshow.nfo} if it was changed outside KMM or the details do not come from it yet
     * (Ember import), so that the TV show view shows the values of the NFO and does not write older ones
     * back when saving. An unreadable NFO is ignored here.
     */
    void readShowIfOutdated(TvShow show) {
        try {
            refreshShowIfChangedOutside(show);
        } catch (TvShowProblemException e) {
            log.warn("NFO of TV show {} not read: {}", show.getId(), e.getMessage());
        }
    }

    /** Like {@link #readShowIfOutdated}, for all episodes of a file. */
    void readEpisodesIfOutdated(List<Episode> sameFile) {
        try {
            refreshEpisodesIfChangedOutside(sameFile);
        } catch (TvShowProblemException e) {
            log.warn("NFO of episode file {} not read: {}", sameFile.getFirst().getFilePath(), e.getMessage());
        }
    }

    static void applyShow(TvShow show, TvShowNfo nfo, String fallbackTitle, Integer fallbackYear) {
        TvShowExtras extras = TvNfoMapper.toExtras(nfo);
        if (!show.isNfoRead()) {
            extras = extras.withActors(withMissingActors(extras.actors(), show.extras().actors()));
        }
        show.applyMetadata(TvNfoMapper.toMetadata(nfo, fallbackTitle, fallbackYear));
        if (KmmSettings.isMetadataLanguage(nfo.language())) {
            show.changeMetadataLanguage(nfo.language());
        }
        show.applyExtras(extras);
        if (nfo.trailer() != null) {
            show.changeTrailer(nfo.trailer());
        }
        show.markNfoRead();
    }

    /**
     * Reads the trailer from the NFO afterwards if KMM does not know it yet, e.g. for TV shows from a version without
     * trailers. Without an NFO there is no trailer.
     *
     * @return whether the trailer is known afterwards; {@code false} if the NFO is unreadable
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

    /** The episode in the NFO; a file with only one episode may also contain one numbered differently. */
    static Optional<EpisodeNfo> matching(Episode episode, List<EpisodeNfo> episodes, boolean singleEpisodeFile) {
        return episodes.stream()
                .filter(nfo -> nfo.describes(episode.getSeason(), episode.getEpisode()))
                .findFirst()
                .or(() -> singleEpisodeFile && episodes.size() == 1 ? Optional.of(episodes.getFirst())
                        : Optional.empty());
    }

    /**
     * Takes over the NFO. If the details so far do not come from the NFO (Ember import), actors that exist only in
     * Ember's data (e.g. guest stars) are kept, and so are the known technical details if the NFO has none.
     */
    void applyEpisode(Episode episode, EpisodeNfo nfo) {
        EpisodeExtras extras = TvNfoMapper.toExtras(nfo, clock.getZone());
        if (!episode.isNfoRead()) {
            EpisodeExtras known = episode.extras();
            extras = extras.withActors(withMissingActors(extras.actors(), known.actors()));
            if (extras.streams().isEmpty()) {
                extras = extras.withStreams(known.streams());
            }
        }
        episode.applyMetadata(TvNfoMapper.toMetadata(nfo, episode.getTitle()));
        episode.applyExtras(extras);
        episode.markNfoRead();
        Instant dateAdded = TvNfoMapper.toInstant(nfo.dateAdded(), clock.getZone());
        if (dateAdded != null) {
            episode.changeAddedAt(dateAdded);
        }
    }

    /** The actors of the NFO, followed by the known ones that are missing from the NFO. */
    static List<TvActor> withMissingActors(List<TvActor> fromNfo, List<TvActor> known) {
        Set<String> names = fromNfo.stream().map(TvActor::name).collect(Collectors.toSet());
        return Stream.concat(fromNfo.stream(), known.stream().filter(actor -> !names.contains(actor.name())))
                .toList();
    }

    private static <T> T read(TvNfoState nfo, Function<TvNfoState, T> reader) {
        try {
            return reader.apply(nfo);
        } catch (NfoException e) {
            throw TvShowProblemException.nfoChangedOutside(nfo.file(), e);
        }
    }
}
