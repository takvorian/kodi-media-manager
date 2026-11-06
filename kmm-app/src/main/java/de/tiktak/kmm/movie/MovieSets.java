package de.tiktak.kmm.movie;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Finds movie sets by their name and creates unknown ones.
 */
@Component
@RequiredArgsConstructor
class MovieSets {

    private final MovieSetRepository repository;

    /**
     * @return the set for the reference (with the description taken over) or {@code null} without a reference
     */
    MovieSet resolve(MovieSupplement.SetReference reference) {
        if (reference == null) {
            return null;
        }
        MovieSet set = repository.findByName(reference.name())
                .orElseGet(() -> repository.save(new MovieSet(reference.name())));
        set.describe(reference.overview());
        if (set.getTmdbCollectionId() == null) {
            set.identify(reference.tmdbCollectionId());
        }
        return set;
    }
}
