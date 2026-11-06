package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;

/**
 * Plans the renaming of the TV shows of a source or of a single TV show, without changing anything.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
class TvShowRenamePlanning {

    private static final Comparator<Episode> BY_NUMBER = Comparator.comparingInt(Episode::getSeason)
            .thenComparingInt(Episode::getEpisode);

    private final MediaSourceService sourceService;
    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;

    /**
     * @return the plan per TV show of the source, sorted by folder
     * @throws TvRenameProblemException if the source contains no TV shows
     */
    Planned planSource(long sourceId, TvShowRenamePlanner.Patterns patterns) {
        MediaSource source = tvSource(sourceId);
        List<TvShow> shows = showRepository.findBySourceId(sourceId);
        Map<Long, List<Episode>> episodes = episodeRepository.findByTvShowIdIn(
                        shows.stream().map(TvShow::getId).toList()).stream()
                .collect(Collectors.groupingBy(Episode::getTvShowId));
        Path root = Path.of(source.getPath()).toAbsolutePath().normalize();
        List<TvShowRenamePlan> plans = shows.stream()
                .map(show -> TvShowRenamePlanner.plan(candidate(show, episodes.getOrDefault(show.getId(), List.of()),
                        root), patterns))
                .sorted((a, b) -> a.currentDirectory().compareToIgnoreCase(b.currentDirectory()))
                .toList();
        return new Planned(root, plans);
    }

    /**
     * @throws TvShowProblemException if the TV show does not exist
     */
    Planned planShow(long showId, TvShowRenamePlanner.Patterns patterns) {
        TvShow show = showRepository.findById(showId).orElseThrow(() -> TvShowProblemException.showNotFound(showId));
        MediaSource source = tvSource(show.getSourceId());
        Path root = Path.of(source.getPath()).toAbsolutePath().normalize();
        List<Episode> episodes = episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(showId);
        return new Planned(root, List.of(TvShowRenamePlanner.plan(candidate(show, episodes, root), patterns)));
    }

    /**
     * Plans only the episodes of a season; the TV show folder stays as it is.
     *
     * @throws TvShowProblemException if the TV show does not exist
     */
    Planned planSeason(long showId, int season, TvShowRenamePlanner.Patterns patterns) {
        TvShow show = showRepository.findById(showId).orElseThrow(() -> TvShowProblemException.showNotFound(showId));
        MediaSource source = tvSource(show.getSourceId());
        Path root = Path.of(source.getPath()).toAbsolutePath().normalize();
        List<Episode> episodes = episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(showId).stream()
                .filter(episode -> episode.getSeason() == season)
                .toList();
        TvShowRenamePlanner.Candidate candidate = candidate(show, episodes, root);
        TvShowRenamePlanner.Candidate keepFolder = new TvShowRenamePlanner.Candidate(candidate.showId(),
                candidate.title(), candidate.showDirectory(), root, false, candidate.values(), candidate.files());
        return new Planned(root, List.of(TvShowRenamePlanner.plan(keepFolder, patterns)));
    }

    private MediaSource tvSource(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        if (source.getMediaType() != MediaType.TV_SHOW) {
            throw TvRenameProblemException.notATvSource(source);
        }
        return source;
    }

    private static TvShowRenamePlanner.Candidate candidate(TvShow show, List<Episode> episodes, Path root) {
        Path directory = show.directory().toAbsolutePath().normalize();
        Map<String, List<Episode>> byFile = episodes.stream()
                .collect(Collectors.groupingBy(Episode::getFilePath));
        List<TvShowRenamePlanner.CandidateFile> files = byFile.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new TvShowRenamePlanner.CandidateFile(Path.of(entry.getKey()),
                        entry.getValue().stream().sorted(BY_NUMBER).map(TvNameValues.EpisodeValues::of).toList()))
                .toList();
        return new TvShowRenamePlanner.Candidate(show.getId(), show.getTitle(), directory, root,
                !directory.equals(root), TvNameValues.of(show), files);
    }

    /** Plans and the root of the source, which cleanup of empty folders does not go above. */
    record Planned(Path sourceRoot, List<TvShowRenamePlan> plans) {

        Planned {
            plans = List.copyOf(plans);
        }
    }
}
