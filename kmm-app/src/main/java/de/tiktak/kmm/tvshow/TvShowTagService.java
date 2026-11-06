package de.tiktak.kmm.tvshow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Tags of TV shows: count, find and change. Changed tags are written to the
 * {@code tvshow.nfo} and the database, as when saving the metadata.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TvShowTagService {

    private final TvShowRepository showRepository;
    private final TvShowMetadataService metadataService;

    /** @return the number of TV shows per tag */
    public Map<String, Long> countByTag() {
        return showRepository.countByTag().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }

    public List<Long> findIdsByTag(String tag) {
        return showRepository.findIdsByTag(tag);
    }

    /** @return ID and title of the TV shows with the tag, sorted by title */
    public Map<Long, String> findTitlesByTag(String tag) {
        return showRepository.findTitlesByTag(tag).stream().collect(Collectors.toMap(row -> (Long) row[0],
                row -> (String) row[1], (first, second) -> first, LinkedHashMap::new));
    }

    /**
     * @param change maps the previous tags to the new ones
     * @return whether the tags changed
     * @throws TvShowProblemException if the TV show does not exist or the NFO cannot be written
     */
    @Transactional
    public boolean changeTags(long showId, UnaryOperator<List<String>> change) {
        TvShowMetadata current = metadataService.currentMetadata(showId);
        TvShowMetadata changed = current.withTags(change.apply(current.tags()));
        if (changed.tags().equals(current.tags())) {
            return false;
        }
        metadataService.updateShow(showId, changed);
        return true;
    }
}
