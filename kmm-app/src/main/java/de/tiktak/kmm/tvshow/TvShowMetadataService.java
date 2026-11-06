package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.scanner.TvShowFiles;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads and changes TV shows and episodes. Changes are written to the NFO first; if that
 * fails, the database stays unchanged as well.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TvShowMetadataService {

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final TvShowNfoFiles nfoFiles;
    private final TvNfoSync nfoSync;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final MediaSourceService sourceService;

    /**
     * The metadata of a TV show after an NFO changed outside KMM has been read; for changes that
     * build on the existing metadata.
     */
    @Transactional
    TvShowMetadata currentMetadata(long showId) {
        TvShow show = requireShow(showId);
        nfoSync.refreshShowIfChangedOutside(show);
        return show.metadata();
    }

    public TvShowDetails findShow(long showId) {
        TvShow show = requireShow(showId);
        return details(show);
    }

    /**
     * The TV show for the TV show view. An NFO changed outside KMM, or one the details do not yet come from
     * (Ember import), is read first; otherwise saving would write older values back.
     */
    @Transactional
    public TvShowDetails openShow(long showId) {
        TvShow show = requireShow(showId);
        nfoSync.readShowIfOutdated(show);
        return details(show);
    }

    /** The language for scraping this TV show and its episodes; {@code null} means the settings' language. */
    @Transactional(readOnly = true)
    public String scrapeLanguage(long showId) {
        TvShow show = requireShow(showId);
        return show.getMetadataLanguage() != null ? show.getMetadataLanguage()
                : sourceService.languageOf(show.getSourceId());
    }

    /**
     * Changes the TV show's own language in the database and NFO. An NFO changed outside KMM is read
     * first.
     *
     * @param language language, or {@code null} to use the source's or the settings' language
     * @return whether anything changed
     * @throws TvShowProblemException if the NFO cannot be read or written
     */
    @Transactional
    public boolean changeLanguage(long showId, String language) {
        TvShow show = requireShow(showId);
        nfoSync.refreshShowIfChangedOutside(show);
        if (!show.changeMetadataLanguage(language)) {
            return false;
        }
        writeLanguage(show);
        log.info("Language of TV show {} set to {}", showId, language);
        return true;
    }

    /** Enters its own language into a just-created NFO. */
    @Transactional
    public void storeLanguageInNfo(long showId) {
        TvShow show = requireShow(showId);
        if (show.getMetadataLanguage() != null) {
            writeLanguage(show);
        }
    }

    private void writeLanguage(TvShow show) {
        nfoFiles.findShowNfo(show.directory()).ifPresent(nfo -> show.markNfoSynchronized(
                write(() -> nfoFiles.writeShowLanguage(nfo, show.getMetadataLanguage())).modifiedAt()));
    }

    @Transactional
    public TvShowDetails updateShow(long showId, TvShowMetadata metadata) {
        TvShow show = requireShow(showId);
        nfoSync.refreshShowIfChangedOutside(show);
        TvNfoState written = write(() -> nfoFiles.writeShow(show.directory(), TvNfoMapper.toNfo(metadata,
                show.extras(), show.nfoTrailer())));
        show.applyMetadata(metadata);
        show.markNfoSynchronized(written.modifiedAt());
        log.info("Metadata of TV show {} saved to {}", showId, written.file());
        events.publishEvent(new TvShowFilesChangedEvent(showId));
        return details(show);
    }

    /**
     * Changes the metadata of a TV show starting from the current state, e.g. in batch editing. An NFO changed
     * outside KMM is read first.
     *
     * @return whether anything changed
     * @throws TvShowProblemException if the NFO cannot be read or written
     */
    @Transactional
    public boolean changeShow(long showId, UnaryOperator<TvShowMetadata> change) {
        TvShow show = requireShow(showId);
        nfoSync.refreshShowIfChangedOutside(show);
        TvShowMetadata current = show.metadata();
        TvShowMetadata changed = change.apply(current);
        if (changed.equals(current)) {
            return false;
        }
        TvNfoState written = write(() -> nfoFiles.writeShow(show.directory(), TvNfoMapper.toNfo(changed,
                show.extras(), show.nfoTrailer())));
        show.applyMetadata(changed);
        show.markNfoSynchronized(written.modifiedAt());
        events.publishEvent(new TvShowFilesChangedEvent(showId));
        return true;
    }

    /**
     * Changes the trailer link of a TV show in the database and NFO. An NFO changed outside KMM is read
     * first; in an existing NFO only {@code <trailer>} changes, otherwise it is created anew.
     *
     * @param trailer link, or {@code null} for "no trailer"
     * @throws TvShowProblemException if the NFO cannot be read or written
     */
    @Transactional
    public TvShowDetails changeTrailer(long showId, String trailer) {
        TvShow show = requireShow(showId);
        nfoSync.refreshShowIfChangedOutside(show);
        show.changeTrailer(trailer);
        Optional<TvNfoState> nfo = nfoFiles.findShowNfo(show.directory());
        TvNfoState written = write(() -> nfo.isPresent()
                ? nfoFiles.writeShowTrailer(nfo.get(), show.getTrailer())
                : nfoFiles.writeShow(show.directory(), TvNfoMapper.toNfo(show.metadata(), show.extras(),
                        show.nfoTrailer())));
        show.markNfoSynchronized(written.modifiedAt());
        log.info("Trailer of TV show {} set to {}", showId, trailer);
        events.publishEvent(new TvShowFilesChangedEvent(showId));
        return details(show);
    }

    /**
     * Changes metadata and further details of a TV show (such as ratings) starting from the current state in a
     * single write; an NFO changed outside KMM or not yet read is read first.
     *
     * @return whether anything changed
     * @throws TvShowProblemException if the NFO cannot be read or written
     */
    @Transactional
    public boolean changeContent(long showId, UnaryOperator<TvShowContent> change) {
        TvShow show = requireShow(showId);
        nfoSync.refreshShowIfChangedOutside(show);
        TvShowContent current = new TvShowContent(show.metadata(), show.extras());
        TvShowContent changed = change.apply(current);
        if (changed.equals(current)) {
            return false;
        }
        TvNfoState written = write(() -> nfoFiles.writeShow(show.directory(), TvNfoMapper.toNfo(changed.metadata(),
                changed.extras(), show.nfoTrailer())));
        show.applyMetadata(changed.metadata());
        show.applyExtras(changed.extras());
        show.markNfoSynchronized(written.modifiedAt());
        events.publishEvent(new TvShowFilesChangedEvent(showId));
        return true;
    }

    public EpisodeDetails findEpisode(long episodeId) {
        Episode episode = requireEpisode(episodeId);
        return episodeDetails(episode);
    }

    /** The episode for the episode view; an outdated NFO is read first, as with {@link #openShow}. */
    @Transactional
    public EpisodeDetails openEpisode(long episodeId) {
        Episode episode = requireEpisode(episodeId);
        nfoSync.readEpisodesIfOutdated(episodeRepository.findByFilePathHash(episode.getFilePathHash()));
        return episodeDetails(episode);
    }

    /**
     * Saves the metadata of an episode. For multi-episode files the shared NFO is written with all
     * episodes of the file.
     */
    @Transactional
    public EpisodeDetails updateEpisode(long episodeId, EpisodeMetadata metadata) {
        Episode episode = requireEpisode(episodeId);
        List<Episode> sameFile = episodeRepository.findByFilePathHash(episode.getFilePathHash());
        nfoSync.refreshEpisodesIfChangedOutside(sameFile);
        List<EpisodeNfo> nfos = sameFile.stream()
                .map(other -> TvNfoMapper.toNfo(other.getSeason(), other.getEpisode(),
                        other.getId().equals(episode.getId()) ? metadata : other.metadata(), other.extras(),
                        other.getAddedAt(), clock.getZone()))
                .toList();
        TvNfoState written = write(() -> nfoFiles.writeEpisodes(episode.file(), nfos));
        episode.applyMetadata(metadata);
        sameFile.forEach(other -> other.markNfoSynchronized(written.modifiedAt()));
        log.info("Metadata of episode {} saved to {}", episodeId, written.file());
        sameFile.forEach(other -> events.publishEvent(new EpisodeFilesChangedEvent(other.getId())));
        return episodeDetails(episode);
    }

    private TvShowDetails details(TvShow show) {
        return TvShowDetails.from(show, episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(show.getId()),
                TvNfoInfo.of(TvShowFiles.tvShowNfo(show.directory()), nfoFiles.findShowNfo(show.directory())));
    }

    private EpisodeDetails episodeDetails(Episode episode) {
        return EpisodeDetails.from(episode, requireShow(episode.getTvShowId()),
                TvNfoInfo.of(TvShowFiles.episodeNfo(episode.file()), nfoFiles.findEpisodeNfo(episode.file())));
    }

    private static TvNfoState write(NfoWrite write) {
        try {
            return write.run();
        } catch (NfoException e) {
            throw TvShowProblemException.nfoNotWritable(e);
        }
    }

    private TvShow requireShow(long showId) {
        return showRepository.findById(showId).orElseThrow(() -> TvShowProblemException.showNotFound(showId));
    }

    private Episode requireEpisode(long episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> TvShowProblemException.episodeNotFound(episodeId));
    }

    @FunctionalInterface
    private interface NfoWrite {
        TvNfoState run();
    }
}
