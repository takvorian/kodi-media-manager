package de.tiktak.kmm.tvshow;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Liest die Serienliste. Öffentliche Schnittstelle des Moduls für andere Module.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TvShowQueryService {

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;

    /**
     * @param search Teil des Titels; leer oder {@code null} liefert alle Serien
     */
    public Page<TvShowSummary> findShows(String search, Pageable pageable) {
        Page<TvShow> shows = search == null || search.isBlank()
                ? showRepository.findAll(pageable)
                : showRepository.findByTitleContainingIgnoreCase(search.strip(), pageable);
        Map<Long, Long> episodeCounts = episodeCounts(shows.getContent().stream().map(TvShow::getId).toList());
        return shows.map(show -> TvShowSummary.from(show, episodeCounts.getOrDefault(show.getId(), 0L)));
    }

    /** IDs der Serien einer Quelle, nach Titel sortiert. */
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
