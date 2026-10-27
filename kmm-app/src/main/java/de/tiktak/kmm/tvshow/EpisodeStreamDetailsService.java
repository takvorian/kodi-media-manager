package de.tiktak.kmm.tvshow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.media.StreamDetailsMerge;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.StreamDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Übernimmt neu ausgelesene technische Angaben einer Episodendatei in die vorhandene NFO und die
 * Datenbank; ohne NFO nur in die Datenbank. Alle Episoden einer Mehrfachepisoden-Datei bekommen die
 * Angaben, denn sie teilen sich die Datei. Sprachen und 3D-Angaben, die ffprobe nicht kennt, bleiben
 * je Episode erhalten.
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

    public EpisodeMedia find(long episodeId) {
        Episode episode = require(episodeId);
        String showTitle = showRepository.findById(episode.getTvShowId()).map(TvShow::getTitle)
                .orElseThrow(() -> TvShowProblemException.showNotFound(episode.getTvShowId()));
        return EpisodeMedia.of(episode, showTitle);
    }

    /**
     * @return die übernommenen Angaben der angegebenen Episode
     * @throws TvShowProblemException wenn die NFO nicht geschrieben werden kann; die Datenbank bleibt dann
     *                                unverändert
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
     * Episoden, die vorher auf dem Stand der Datei waren, bleiben aktuell; sonst liest der nächste Scan
     * die NFO für sie neu ein.
     */
    private void write(TvNfoState before, Map<Episode, StreamDetails> merged) {
        try {
            TvNfoState written = before;
            for (Map.Entry<Episode, StreamDetails> entry : merged.entrySet()) {
                Episode episode = entry.getKey();
                written = nfoFiles.writeStreamDetails(before, episode.getSeason(), episode.getEpisode(),
                        entry.getValue());
            }
            TvNfoState last = written;
            merged.keySet().stream()
                    .filter(episode -> before.modifiedAt().equals(episode.getNfoModifiedAt()))
                    .forEach(episode -> episode.markNfoSynchronized(last.modifiedAt()));
        } catch (NfoException e) {
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
