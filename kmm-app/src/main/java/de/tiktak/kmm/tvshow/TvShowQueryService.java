package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.persistence.StreamIndex;
import lombok.RequiredArgsConstructor;

/**
 * Reads the TV show list. Public interface of the module for other modules.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TvShowQueryService {

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final Clock clock;

    /**
     * @param search part of the title; empty or {@code null} returns all TV shows
     */
    public Page<TvShowSummary> findShows(String search, Pageable pageable) {
        Page<TvShow> shows = search == null || search.isBlank()
                ? showRepository.findAll(pageable)
                : showRepository.findByTitleContainingIgnoreCase(search.strip(), pageable);
        Map<Long, Long> episodeCounts = episodeCounts(shows.getContent().stream().map(TvShow::getId).toList());
        return shows.map(show -> TvShowSummary.from(show, episodeCounts.getOrDefault(show.getId(), 0L)));
    }

    /**
     * @throws de.tiktak.kmm.filter.InvalidFilterException if a condition does not fit its field
     */
    public Page<TvShowSummary> findFiltered(TvShowFilter filter, Pageable pageable) {
        Page<TvShow> shows = showRepository.findAll(TvShowFilterSpecification.of(filter, clock), pageable);
        Map<Long, Long> episodeCounts = episodeCounts(shows.getContent().stream().map(TvShow::getId).toList());
        return shows.map(show -> TvShowSummary.from(show, episodeCounts.getOrDefault(show.getId(), 0L)));
    }

    /** IDs of all TV shows matching the filter, e.g. for batch editing. */
    public List<Long> findShowIds(TvShowFilter filter) {
        return showRepository.findAll(TvShowFilterSpecification.of(filter, clock)).stream().map(TvShow::getId)
                .toList();
    }

    public TvShowFilterValues filterValues() {
        return new TvShowFilterValues(
                TvShowFilterValues.sorted(showRepository.findDistinctGenres()),
                TvShowFilterValues.sorted(showRepository.findDistinctTags()),
                TvShowFilterValues.sorted(showRepository.findDistinctStudios()),
                TvShowFilterValues.sorted(showRepository.findDistinctStatuses()),
                TvShowFilterValues.sorted(showRepository.findDistinctMpaa()),
                TvShowFilterValues.sorted(episodeRepository.findDistinctVideoSources()),
                episodeRepository.findDistinctResolutions().stream().sorted().toList(),
                TvShowFilterValues.sorted(episodeRepository.findDistinctVideoCodecs()),
                TvShowFilterValues.sorted(tokens(episodeRepository.findDistinctAudioCodecLists())),
                TvShowFilterValues.sorted(tokens(episodeRepository.findDistinctAudioLanguageLists())),
                TvShowFilterValues.sorted(tokens(episodeRepository.findDistinctSubtitleLanguageLists())));
    }

    private static List<String> tokens(Collection<String> lists) {
        return lists.stream().flatMap(list -> StreamIndex.tokensOf(list).stream()).toList();
    }

    /** IDs of the TV shows of a source, sorted by title. */
    public List<Long> findShowIds(long sourceId) {
        return showRepository.findBySourceId(sourceId).stream()
                .sorted(Comparator.comparing(TvShow::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(TvShow::getId)
                .toList();
    }

    private Map<Long, Long> episodeCounts(List<Long> showIds) {
        if (showIds.isEmpty()) {
            return Map.of();
        }
        return episodeRepository.countByTvShowIds(showIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }
}
