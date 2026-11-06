package de.tiktak.kmm.filter;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Saved filters for movie and TV show lists. Names are unique per list,
 * ignoring case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavedFilterService {

    static final int MAX_DEFINITION_LENGTH = 20_000;

    private final SavedFilterRepository repository;
    private final ObjectMapper objectMapper;

    /** @return the filters of the list, sorted by name */
    @Transactional(readOnly = true)
    public List<SavedFilterView> findAll(FilterMediaType mediaType) {
        return repository.findByMediaType(mediaType).stream()
                .sorted(Comparator.comparing(SavedFilter::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::view)
                .toList();
    }

    /**
     * @throws SavedFilterProblemException if the name is taken or the filter is too large
     */
    @Transactional
    public SavedFilterView create(SavedFilterData data) {
        requireFreeName(data, null);
        SavedFilter saved = repository.save(new SavedFilter(data.mediaType(), data.name(), json(data.definition())));
        log.info("Saved filter {} '{}' created", saved.getId(), saved.getName());
        return view(saved);
    }

    /**
     * @throws SavedFilterProblemException if the filter does not exist for this list, the name is taken or the
     *                                     filter is too large
     */
    @Transactional
    public SavedFilterView update(long id, SavedFilterData data) {
        SavedFilter filter = require(id);
        if (filter.getMediaType() != data.mediaType()) {
            throw SavedFilterProblemException.notFound(id);
        }
        requireFreeName(data, id);
        filter.change(data.name(), json(data.definition()));
        return view(filter);
    }

    @Transactional
    public void delete(long id) {
        repository.delete(require(id));
    }

    /** Takes over a filter from a migration; one with the same name is replaced. */
    @Transactional
    public void importFilter(SavedFilterData data) {
        repository.findByMediaTypeAndNameIgnoreCase(data.mediaType(), data.name()).ifPresentOrElse(
                existing -> existing.change(data.name(), json(data.definition())),
                () -> repository.save(new SavedFilter(data.mediaType(), data.name(), json(data.definition()))));
    }

    private void requireFreeName(SavedFilterData data, Long ownId) {
        repository.findByMediaTypeAndNameIgnoreCase(data.mediaType(), data.name())
                .filter(other -> !other.getId().equals(ownId))
                .ifPresent(other -> {
                    throw SavedFilterProblemException.nameTaken(data.name());
                });
    }

    private SavedFilter require(long id) {
        return repository.findById(id).orElseThrow(() -> SavedFilterProblemException.notFound(id));
    }

    private String json(JsonNode definition) {
        String json = objectMapper.writeValueAsString(definition);
        if (json.length() > MAX_DEFINITION_LENGTH) {
            throw SavedFilterProblemException.tooLong();
        }
        return json;
    }

    private SavedFilterView view(SavedFilter filter) {
        return new SavedFilterView(filter.getId(), filter.getMediaType(), filter.getName(),
                objectMapper.readTree(filter.getDefinition()));
    }
}
