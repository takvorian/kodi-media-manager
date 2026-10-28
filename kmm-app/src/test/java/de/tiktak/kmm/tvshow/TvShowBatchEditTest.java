package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.filter.FieldChange;
import de.tiktak.kmm.filter.ListChange;

class TvShowBatchEditTest {

    private static final TvShowMetadata ALF = new TvShowMetadata("ALF", null, null, 1986, null, "Plot", 25, "FSK 6",
            "Continuing", List.of("Comedy"), List.of("NBC"), List.of("Kult"), "78020", null, null, 9);

    @Test
    void changesGenresTagsRatingAndStatusAndKeepsTheRest() {
        TvShowBatchEdit edit = new TvShowBatchEdit(List.of(7L, 7L),
                new ListChange(List.of("Familie"), List.of("comedy")), new ListChange(List.of("Neu"), null),
                new FieldChange(true, null), new FieldChange(false, "Ended"));

        TvShowMetadata changed = edit.apply(ALF);

        assertThat(edit.showIds()).containsExactly(7L);
        assertThat(changed.genres()).containsExactly("Familie");
        assertThat(changed.tags()).containsExactly("Kult", "Neu");
        assertThat(changed.mpaa()).isNull();
        assertThat(changed.status()).isEqualTo("Ended");
        assertThat(changed).usingRecursiveComparison().ignoringFields("genres", "tags", "mpaa", "status")
                .isEqualTo(ALF);
    }

    @Test
    void leavesEverythingWithoutChangesAndChecksShortFields() {
        TvShowBatchEdit nothing = new TvShowBatchEdit(List.of(7L), null, null, null, null);

        assertThat(nothing.apply(ALF)).isEqualTo(ALF);
        assertThat(nothing.isShortEnough()).isTrue();
        assertThat(new TvShowBatchEdit(List.of(7L), null, null, null, new FieldChange(false, "x".repeat(65)))
                .isShortEnough()).isFalse();
    }
}
