package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.NfoPlayback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads the playback state and own rating of episodes and takes over new ones, e.g. from Kodi. The state is written to
 * the existing NFO and to the database; without an NFO only to the database. No
 * {@link EpisodeFilesChangedEvent} is published: the state comes from Kodi, so Kodi does not have to
 * re-read the episode.
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
     * @param playCount    number of plays or {@code null}
     * @param lastPlayedAt last play or {@code null}
     * @param userRating   own rating 1-10 or {@code null}
     * @throws TvShowProblemException if the NFO cannot be written; the database then stays
     *                                unchanged
     */
    @Transactional
    public void changePlayback(long episodeId, Integer playCount, Instant lastPlayedAt, Integer userRating) {
        Episode episode = require(episodeId);
        nfoFiles.findEpisodeNfo(episode.file()).ifPresent(nfo -> {
            try {
                TvNfoState written = nfoFiles.writePlayback(nfo, episode.getSeason(), episode.getEpisode(),
                        new NfoPlayback(playCount, TvNfoMapper.toLocal(lastPlayedAt, clock.getZone()), userRating));
                markSynchronized(episode, nfo, written);
            } catch (NfoException e) {
                throw TvShowProblemException.nfoNotWritable(e);
            }
        });
        episode.changePlayback(playCount, lastPlayedAt, userRating);
        log.info("Playback of episode {} changed to {} plays, last {}, rating {}", episodeId, playCount,
                lastPlayedAt, userRating);
    }

    /**
     * The other episodes of a multi-episode NFO stay up to date if they were in sync with the file
     * before; otherwise the next scan re-reads the NFO for them.
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
