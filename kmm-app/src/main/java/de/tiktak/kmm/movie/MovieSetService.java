package de.tiktak.kmm.movie;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Movie sets: list, create, change and delete. The movies of a set are in its order (year or
 * title, as in Ember). Whatever changes movie NFOs is started by {@link MovieSetJobService} as a background job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieSetService {

    private final MovieSetRepository setRepository;
    private final MovieRepository movieRepository;
    private final ApplicationEventPublisher events;

    /** @return all sets with the number of their movies, sorted by name */
    public List<MovieSetSummary> findAll() {
        Map<Long, Long> counts = movieRepository.countByMovieSet().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return setRepository.findAll().stream()
                .sorted(Comparator.comparing(set -> set.getName().toLowerCase(Locale.ROOT)))
                .map(set -> new MovieSetSummary(set.getId(), set.getName(), set.getOverview(), set.getTmdbCollectionId(),
                        set.getSortMethod(), counts.getOrDefault(set.getId(), 0L)))
                .toList();
    }

    /**
     * @throws MovieSetProblemException if the set does not exist
     */
    public MovieSetDetails find(long setId) {
        MovieSet set = require(setId);
        return new MovieSetDetails(set.getId(), data(set), members(set));
    }

    /**
     * @throws MovieSetProblemException if the name is taken
     */
    @Transactional
    public MovieSetDetails create(MovieSetData data) {
        requireFreeName(data.name(), null);
        MovieSet set = new MovieSet(data.name());
        set.change(data);
        setRepository.save(set);
        log.info("Movie set {} '{}' created", set.getId(), set.getName());
        return new MovieSetDetails(set.getId(), data(set), List.of());
    }

    /**
     * Takes over the details. If name or description change, the movies' NFOs have to be rewritten.
     *
     * @return the movies whose NFO has to be rewritten
     * @throws MovieSetProblemException if the set does not exist or the name is taken
     */
    @Transactional
    public List<Long> update(long setId, MovieSetData data) {
        MovieSet set = require(setId);
        requireFreeName(data.name(), setId);
        String previousName = set.getName();
        boolean nfoChanges = !previousName.equals(data.name()) || !Objects.equals(set.getOverview(), data.overview());
        set.change(data);
        log.info("Movie set {} '{}' changed", setId, set.getName());
        if (!nfoChanges) {
            return List.of();
        }
        events.publishEvent(new MovieSetChangedEvent(setId, previousName));
        return movieRepository.findIdsByMovieSetId(setId);
    }

    /**
     * Takes over a set from a migration: creates it or supplements the one with the same name; existing details are
     * kept if the migration has none.
     */
    @Transactional
    public void importSet(String name, String overview, String tmdbCollectionId, SetSortMethod sortMethod) {
        MovieSet set = setRepository.findByName(name).orElseGet(() -> setRepository.save(new MovieSet(name)));
        set.describe(overview);
        set.identify(tmdbCollectionId);
        set.sortBy(sortMethod);
    }

    /** @return the movies of the set */
    public List<Long> findMovieIds(long setId) {
        require(setId);
        return movieRepository.findIdsByMovieSetId(setId);
    }

    /** Deletes a set that has no movies left. */
    @Transactional
    public void deleteEmpty(long setId) {
        MovieSet set = require(setId);
        if (!movieRepository.findIdsByMovieSetId(setId).isEmpty()) {
            throw new IllegalStateException("Movie set %d still has movies".formatted(setId));
        }
        setRepository.delete(set);
        log.info("Movie set {} '{}' deleted", setId, set.getName());
    }

    private List<MovieSetDetails.Member> members(MovieSet set) {
        Comparator<Row> byTitle = Comparator.comparing(Row::sortKey);
        Comparator<Row> order = set.getSortMethod() == SetSortMethod.TITLE ? byTitle
                : Comparator.comparing((Row row) -> row.member().year(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(byTitle);
        return movieRepository.findMembersByMovieSetId(set.getId()).stream()
                .map(Row::of)
                .sorted(order)
                .map(Row::member)
                .toList();
    }

    private void requireFreeName(String name, Long ownId) {
        setRepository.findAll().stream()
                .filter(other -> !other.getId().equals(ownId))
                .filter(other -> other.getName().equalsIgnoreCase(name))
                .findAny()
                .ifPresent(other -> {
                    throw MovieSetProblemException.nameTaken(name);
                });
    }

    private MovieSet require(long setId) {
        return setRepository.findById(setId).orElseThrow(() -> MovieSetProblemException.notFound(setId));
    }

    /** A movie of the set with the key for sorting by (sort) title. */
    private record Row(MovieSetDetails.Member member, String sortKey) {

        static Row of(Object[] row) {
            String title = (String) row[1];
            String sortTitle = (String) row[3];
            return new Row(new MovieSetDetails.Member((Long) row[0], title, (Integer) row[2]),
                    (sortTitle != null ? sortTitle : title).toLowerCase(Locale.ROOT));
        }
    }

    private static MovieSetData data(MovieSet set) {
        return new MovieSetData(set.getName(), set.getOverview(), set.getTmdbCollectionId(), set.getSortMethod());
    }
}
