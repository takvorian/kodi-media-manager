package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.filter.FieldChange;
import de.tiktak.kmm.filter.ListChange;

class MovieBatchEditTest {

    private static final MovieMetadata HEAT = new MovieMetadata("Heat", null, null, 1995, null, "Plot", null, null,
            170, "FSK 16", List.of("Krimi", "Drama"), "tt0113277", "949", "Director's Cut", "bluray");

    @Test
    void addsAndRemovesGenresIgnoringCaseAndKeepsTheRest() {
        MovieBatchEdit edit = new MovieBatchEdit(List.of(1L, 1L, 2L),
                new ListChange(List.of(" krimi ", "Klassiker", ""), List.of("DRAMA")), null, null,
                null, null);

        MovieMetadata changed = edit.apply(HEAT);

        assertThat(edit.movieIds()).containsExactly(1L, 2L);
        assertThat(changed.genres()).containsExactly("Krimi", "Klassiker");
        assertThat(changed).usingRecursiveComparison().ignoringFields("genres").isEqualTo(HEAT);
        assertThat(edit.changesMetadata()).isTrue();
    }

    @Test
    void setsOrClearsSingleFields() {
        MovieBatchEdit edit = new MovieBatchEdit(List.of(1L), null, null, new FieldChange(false,
                " FSK 12 "), new FieldChange(true, null), new FieldChange(false, " "));

        MovieMetadata changed = edit.apply(HEAT);

        assertThat(changed.mpaa()).isEqualTo("FSK 12");
        assertThat(changed.edition()).isNull();
        assertThat(changed.videoSource()).isEqualTo("bluray");
        assertThat(changed.genres()).isEqualTo(HEAT.genres());
    }

    @Test
    void knowsWhetherMetadataChangesAndChecksTheLengthOfTheRating() {
        MovieBatchEdit tagsOnly = new MovieBatchEdit(List.of(1L), null,
                new ListChange(List.of("Neu"), null), null, null, null);

        assertThat(tagsOnly.changesMetadata()).isFalse();
        assertThat(tagsOnly.tags().changes()).isTrue();
        assertThat(tagsOnly.isMpaaShortEnough()).isTrue();
        assertThat(new MovieBatchEdit(List.of(1L), null, null, new FieldChange(false, "x".repeat(65)),
                null, null).isMpaaShortEnough()).isFalse();
    }
}
