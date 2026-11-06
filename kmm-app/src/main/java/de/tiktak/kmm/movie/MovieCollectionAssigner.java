package de.tiktak.kmm.movie;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.plugin.api.CollectionReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Assigns a scraped movie to the set of its collection, like Ember's "CollectionsAuto". The set is found via the
 * TMDB ID of the collection, otherwise via the name, otherwise created; another set of the movie is replaced.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieCollectionAssigner {

    private final MovieSetRepository setRepository;
    private final MovieSetMembership membership;

    /**
     * @return whether the set membership changed
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
