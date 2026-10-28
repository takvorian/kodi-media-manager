package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.util.List;
import java.util.function.UnaryOperator;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.scanner.TvShowFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liest und ändert Serien und Episoden. Änderungen werden zuerst in die NFO geschrieben; schlägt das
 * fehl, bleibt auch die Datenbank unverändert.
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

    /**
     * Die Metadaten einer Serie, nachdem eine außerhalb von KMM geänderte NFO eingelesen wurde; für Änderungen, die
     * auf den bisherigen Metadaten aufbauen.
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

    @Transactional
    public TvShowDetails updateShow(long showId, TvShowMetadata metadata) {
        TvShow show = requireShow(showId);
        nfoSync.refreshShowIfChangedOutside(show);
        TvNfoState written = write(() -> nfoFiles.writeShow(show.directory(), TvNfoMapper.toNfo(metadata,
                show.extras())));
        show.applyMetadata(metadata);
        show.markNfoSynchronized(written.modifiedAt());
        log.info("Metadata of TV show {} saved to {}", showId, written.file());
        events.publishEvent(new TvShowFilesChangedEvent(showId));
        return details(show);
    }

    /**
     * Ändert die Metadaten einer Serie ausgehend vom aktuellen Stand, etwa in der Stapel-Bearbeitung. Eine außerhalb
     * von KMM geänderte NFO wird vorher eingelesen.
     *
     * @return ob sich etwas geändert hat
     * @throws TvShowProblemException wenn die NFO nicht gelesen oder geschrieben werden kann
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
                show.extras())));
        show.applyMetadata(changed);
        show.markNfoSynchronized(written.modifiedAt());
        events.publishEvent(new TvShowFilesChangedEvent(showId));
        return true;
    }

    public EpisodeDetails findEpisode(long episodeId) {
        Episode episode = requireEpisode(episodeId);
        return episodeDetails(episode);
    }

    /**
     * Speichert die Metadaten einer Episode. Bei Mehrfachepisoden wird die gemeinsame NFO mit allen
     * Episoden der Datei geschrieben.
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
