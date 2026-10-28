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
 * Plant das Umbenennen der Serien einer Quelle oder einer einzelnen Serie, ohne etwas zu ändern.
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
     * @return je Serie der Quelle der Plan, sortiert nach Ordner
     * @throws TvRenameProblemException wenn die Quelle keine Serien enthält
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
     * @throws TvShowProblemException wenn es die Serie nicht gibt
     */
    Planned planShow(long showId, TvShowRenamePlanner.Patterns patterns) {
        TvShow show = showRepository.findById(showId).orElseThrow(() -> TvShowProblemException.showNotFound(showId));
        MediaSource source = tvSource(show.getSourceId());
        Path root = Path.of(source.getPath()).toAbsolutePath().normalize();
        List<Episode> episodes = episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(showId);
        return new Planned(root, List.of(TvShowRenamePlanner.plan(candidate(show, episodes, root), patterns)));
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

    /** Pläne und die Wurzel der Quelle, die beim Aufräumen leerer Ordner nicht unterschritten wird. */
    record Planned(Path sourceRoot, List<TvShowRenamePlan> plans) {

        Planned {
            plans = List.copyOf(plans);
        }
    }
}
