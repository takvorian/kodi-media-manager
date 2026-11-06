package de.tiktak.kmm.tvshow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.media.StreamDetailsMerge;
import de.tiktak.kmm.nfo.EpisodeStreamDetails;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.StreamDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Takes over newly read technical details of an episode file into the existing NFO and the
 * database; without an NFO only into the database. All episodes of a multi-episode file receive the
 * details because they share the file. Languages and 3D details that ffprobe does not know are kept
 * per episode.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EpisodeStreamDetailsService {

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final TvShowNfoFiles nfoFiles;

    public List<EpisodeMedia> findBySource(long sourceId) {
        Map<Long, String> showTitles = showRepository.findBySourceId(sourceId).stream()
                .collect(Collectors.toMap(TvShow::getId, TvShow::getTitle));
        return episodeRepository.findByTvShowIdIn(showTitles.keySet()).stream()
                .map(episode -> EpisodeMedia.of(episode, showTitles.get(episode.getTvShowId())))
                .toList();
    }

    public List<EpisodeMedia> findByShow(long showId) {
        String showTitle = showRepository.findById(showId).map(TvShow::getTitle)
                .orElseThrow(() -> TvShowProblemException.showNotFound(showId));
        return episodeRepository.findByTvShowIdIn(List.of(showId)).stream()
                .map(episode -> EpisodeMedia.of(episode, showTitle))
                .toList();
    }

    /** @throws TvShowProblemException if the TV show does not exist */
    public String showTitleOf(long showId) {
        return showRepository.findById(showId).map(TvShow::getTitle)
                .orElseThrow(() -> TvShowProblemException.showNotFound(showId));
    }

    /** @throws TvShowProblemException if the TV show does not exist */
    public long sourceIdOf(long showId) {
        return showRepository.findById(showId).map(TvShow::getSourceId)
                .orElseThrow(() -> TvShowProblemException.showNotFound(showId));
    }

    public EpisodeMedia find(long episodeId) {
        Episode episode = require(episodeId);
        String showTitle = showRepository.findById(episode.getTvShowId()).map(TvShow::getTitle)
                .orElseThrow(() -> TvShowProblemException.showNotFound(episode.getTvShowId()));
        return EpisodeMedia.of(episode, showTitle);
    }

    /**
     * @return the details taken over for the given episode
     * @throws TvShowProblemException if the NFO cannot be written; the database then stays
     *                                unchanged
     */
    @Transactional
    public StreamDetails changeStreamDetails(long episodeId, StreamDetails probed) {
        Episode changed = require(episodeId);
        Map<Episode, StreamDetails> merged = new LinkedHashMap<>();
        sameFile(changed).forEach(episode -> merged.put(episode,
                StreamDetailsMerge.keepKnown(probed, episode.streamDetails())));
        Optional<TvNfoState> nfo = nfoFiles.findEpisodeNfo(changed.file());
        nfo.ifPresent(before -> write(before, merged));
        merged.forEach(Episode::applyStreamDetails);
        log.info("Stream details of {} episode(s) in {} changed", merged.size(), changed.getFilePath());
        return merged.get(changed);
    }

    /**
     * Episodes that were in sync with the file before stay up to date; otherwise the next scan
     * re-reads the NFO for them.
     */
    private void write(TvNfoState before, Map<Episode, StreamDetails> merged) {
        List<EpisodeStreamDetails> episodes = merged.entrySet().stream()
                .map(entry -> new EpisodeStreamDetails(entry.getKey().getSeason(), entry.getKey().getEpisode(),
                        entry.getValue()))
                .toList();
        try {
            TvNfoState written = nfoFiles.writeStreamDetails(before, episodes);
            merged.keySet().stream()
                    .filter(episode -> before.modifiedAt().equals(episode.getNfoModifiedAt()))
                    .forEach(episode -> episode.markNfoSynchronized(written.modifiedAt()));
        } catch (NfoException e) {
            log.warn("Stream details not written to {}", before.file(), e);
            throw TvShowProblemException.nfoNotWritable(e);
        }
    }

    private List<Episode> sameFile(Episode changed) {
        List<Episode> siblings = episodeRepository.findByFilePathHash(changed.getFilePathHash()).stream()
                .filter(episode -> episode.getFilePath().equals(changed.getFilePath()))
                .toList();
        return siblings.contains(changed) ? siblings : List.of(changed);
    }

    private Episode require(long episodeId) {
        return episodeRepository.findById(episodeId)
                .orElseThrow(() -> TvShowProblemException.episodeNotFound(episodeId));
    }
}
