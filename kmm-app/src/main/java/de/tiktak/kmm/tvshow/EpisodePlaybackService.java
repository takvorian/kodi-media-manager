package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.NfoException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Liest den Wiedergabestand von Episoden und übernimmt einen neuen, etwa aus Kodi. Der Stand wird in die
 * vorhandene NFO und in die Datenbank geschrieben; ohne NFO nur in die Datenbank. Es wird kein
 * {@link EpisodeFilesChangedEvent} veröffentlicht: Der Stand kommt von Kodi, Kodi muss die Episode
 * deshalb nicht neu einlesen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EpisodePlaybackService {

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final TvShowNfoFiles nfoFiles;
    private final Clock clock;

    public List<EpisodePlayback> findBySource(long sourceId) {
        Map<Long, String> showTitles = showRepository.findBySourceId(sourceId).stream()
                .collect(Collectors.toMap(TvShow::getId, TvShow::getTitle));
        return episodeRepository.findByTvShowIdIn(showTitles.keySet()).stream()
                .map(episode -> EpisodePlayback.of(episode, showTitles.get(episode.getTvShowId())))
                .toList();
    }

    public EpisodePlayback find(long episodeId) {
        Episode episode = require(episodeId);
        String showTitle = showRepository.findById(episode.getTvShowId()).map(TvShow::getTitle)
                .orElseThrow(() -> TvShowProblemException.showNotFound(episode.getTvShowId()));
        return EpisodePlayback.of(episode, showTitle);
    }

    /**
     * @param playCount    Anzahl der Wiedergaben oder {@code null}
     * @param lastPlayedAt letzte Wiedergabe oder {@code null}
     * @throws TvShowProblemException wenn die NFO nicht geschrieben werden kann; die Datenbank bleibt dann
     *                                unverändert
     */
    @Transactional
    public void changePlayback(long episodeId, Integer playCount, Instant lastPlayedAt) {
        Episode episode = require(episodeId);
        nfoFiles.findEpisodeNfo(episode.file()).ifPresent(nfo -> {
            try {
                TvNfoState written = nfoFiles.writePlayback(nfo, episode.getSeason(), episode.getEpisode(),
                        playCount, TvNfoMapper.toLocal(lastPlayedAt, clock.getZone()));
                markSynchronized(episode, nfo, written);
            } catch (NfoException e) {
                throw TvShowProblemException.nfoNotWritable(e);
            }
        });
        episode.changePlayback(playCount, lastPlayedAt);
        log.info("Playback of episode {} changed to {} plays, last {}", episodeId, playCount, lastPlayedAt);
    }

    /**
     * Die übrigen Episoden einer Mehrfachepisoden-NFO bleiben aktuell, wenn sie vorher auf dem Stand der
     * Datei waren; sonst liest der nächste Scan die NFO für sie neu ein.
     */
    private void markSynchronized(Episode changed, TvNfoState before, TvNfoState written) {
        episodeRepository.findByFilePathHash(changed.getFilePathHash()).stream()
                .filter(episode -> episode.getFilePath().equals(changed.getFilePath()))
                .filter(episode -> before.modifiedAt().equals(episode.getNfoModifiedAt()))
                .forEach(episode -> episode.markNfoSynchronized(written.modifiedAt()));
        changed.markNfoSynchronized(written.modifiedAt());
    }

    private Episode require(long episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> TvShowProblemException.episodeNotFound(episodeId));
    }
}
