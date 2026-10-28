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
 * Tags von Serien: zählen, finden und ändern. Geänderte Tags werden wie beim Speichern der Metadaten in die
 * {@code tvshow.nfo} und die Datenbank geschrieben.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TvShowTagService {

    private final TvShowRepository showRepository;
    private final TvShowMetadataService metadataService;

    /** @return je Tag die Anzahl der Serien */
    public Map<String, Long> countByTag() {
        return showRepository.countByTag().stream()
                .collect(Collectors.toMap(row -> (String) row[0], row -> (Long) row[1]));
    }

    public List<Long> findIdsByTag(String tag) {
        return showRepository.findIdsByTag(tag);
    }

    /** @return ID und Titel der Serien mit dem Tag, nach Titel sortiert */
    public Map<Long, String> findTitlesByTag(String tag) {
        return showRepository.findTitlesByTag(tag).stream().collect(Collectors.toMap(row -> (Long) row[0],
                row -> (String) row[1], (first, second) -> first, LinkedHashMap::new));
    }

    /**
     * @param change bildet die bisherigen Tags auf die neuen ab
     * @return ob sich die Tags geändert haben
     * @throws TvShowProblemException wenn es die Serie nicht gibt oder die NFO nicht geschrieben werden kann
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
