package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class MovieSupplementTest {

    @Test
    void cleansListsAndDropsBlankOrDuplicateEntries() {
        var supplement = new MovieSupplement(Arrays.asList(new MovieSupplement.Actor(" Al Pacino ", " ", null), null),
                List.of(" Michael Mann ", "Michael Mann", " "), null, List.of("Warner"), List.of(), List.of("Heist"),
                Arrays.asList(new MovieSupplement.Rating(" imdb ", 8.3, 10, 650000, true), null), null, 9, 2, null);

        assertThat(supplement.actors()).containsExactly(new MovieSupplement.Actor("Al Pacino", null, null));
        assertThat(supplement.directors()).containsExactly("Michael Mann");
        assertThat(supplement.writers()).isEmpty();
        assertThat(supplement.ratings()).extracting(MovieSupplement.Rating::source).containsExactly("imdb");
    }

    @Test
    void truncatesOverlongTexts() {
        var actor = new MovieSupplement.Actor("x".repeat(600), null, "t".repeat(2000));
        var supplement = new MovieSupplement(null, null, null, List.of("s".repeat(300)), null, null, null, null, null,
                null, null);

        assertThat(actor.name()).hasSize(MovieSupplement.MAX_NAME_LENGTH);
        assertThat(actor.thumb()).hasSize(MovieSupplement.MAX_THUMB_LENGTH);
        assertThat(supplement.studios().getFirst()).hasSize(MovieSupplement.MAX_LABEL_LENGTH);
    }

    @Test
    void requiresNamesOfActorsSetsAndRatingSources() {
        assertThatNullPointerException().isThrownBy(() -> new MovieSupplement.Actor(" ", null, null));
        assertThatNullPointerException().isThrownBy(() -> new MovieSupplement.SetReference(null, "Text"));
        assertThatNullPointerException().isThrownBy(() -> new MovieSupplement.Rating("", 1, null, null, false));
    }
}
