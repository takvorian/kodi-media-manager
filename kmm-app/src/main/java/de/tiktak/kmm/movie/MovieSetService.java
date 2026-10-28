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
 * Filmsets: auflisten, anlegen, ändern und löschen. Die Filme eines Sets stehen in dessen Reihenfolge (Jahr oder
 * Titel, wie in Ember). Was Film-NFOs ändert, startet {@link MovieSetJobService} als Hintergrund-Job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieSetService {

    private final MovieSetRepository setRepository;
    private final MovieRepository movieRepository;
    private final ApplicationEventPublisher events;

    /** @return alle Sets mit der Anzahl ihrer Filme, nach Name sortiert */
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
     * @throws MovieSetProblemException wenn es das Set nicht gibt
     */
    public MovieSetDetails find(long setId) {
        MovieSet set = require(setId);
        return new MovieSetDetails(set.getId(), data(set), members(set));
    }

    /**
     * @throws MovieSetProblemException wenn der Name vergeben ist
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
     * Übernimmt die Angaben. Ändern sich Name oder Beschreibung, müssen die NFOs der Filme neu geschrieben werden.
     *
     * @return die Filme, deren NFO neu geschrieben werden muss
     * @throws MovieSetProblemException wenn es das Set nicht gibt oder der Name vergeben ist
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
     * Übernimmt ein Set aus einer Migration: legt es an oder ergänzt das gleichnamige; vorhandene Angaben bleiben,
     * wenn die Migration keine hat.
     */
    @Transactional
    public void importSet(String name, String overview, String tmdbCollectionId, SetSortMethod sortMethod) {
        MovieSet set = setRepository.findByName(name).orElseGet(() -> setRepository.save(new MovieSet(name)));
        set.describe(overview);
        set.identify(tmdbCollectionId);
        set.sortBy(sortMethod);
    }

    /** @return die Filme des Sets */
    public List<Long> findMovieIds(long setId) {
        require(setId);
        return movieRepository.findIdsByMovieSetId(setId);
    }

    /** Löscht ein Set, das keine Filme mehr hat. */
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

    /** Ein Film des Sets mit dem Schlüssel für die Sortierung nach (Sortier-)Titel. */
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
