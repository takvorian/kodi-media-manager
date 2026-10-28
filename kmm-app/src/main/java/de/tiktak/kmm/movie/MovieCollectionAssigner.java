package de.tiktak.kmm.movie;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.plugin.api.CollectionReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ordnet einen gescrapten Film dem Set seiner Sammlung zu, wie Embers „CollectionsAuto“. Das Set wird über die
 * TMDB-ID der Sammlung gefunden, sonst über den Namen, sonst angelegt; ein anderes Set des Films wird ersetzt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieCollectionAssigner {

    private final MovieSetRepository setRepository;
    private final MovieSetMembership membership;

    /**
     * @return ob sich die Set-Zugehörigkeit geändert hat
     */
    @Transactional
    public boolean assign(long movieId, CollectionReference collection) {
        MovieSet set = setRepository.findFirstByTmdbCollectionIdOrderById(collection.providerId())
                .or(() -> setRepository.findFirstByNameIgnoreCase(collection.name()))
                .orElseGet(() -> create(collection));
        if (set.getTmdbCollectionId() == null) {
            set.identify(collection.providerId());
        }
        return membership.assign(movieId, set.getId());
    }

    private MovieSet create(CollectionReference collection) {
        MovieSet set = setRepository.save(new MovieSet(collection.name()));
        log.info("Movie set {} '{}' created for TMDB collection {}", set.getId(), set.getName(),
                collection.providerId());
        return set;
    }
}
