package de.tiktak.kmm.movie;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Findet Filmsets über ihren Namen und legt unbekannte an.
 */
@Component
@RequiredArgsConstructor
class MovieSets {

    private final MovieSetRepository repository;

    /**
     * @return das Set zum Verweis (mit übernommener Beschreibung) oder {@code null} ohne Verweis
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
