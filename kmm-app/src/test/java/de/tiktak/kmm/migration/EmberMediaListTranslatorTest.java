package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tiktak.kmm.filter.FilterOperator;
import de.tiktak.kmm.movie.MovieFilter;
import de.tiktak.kmm.movie.MovieFilterField;

class EmberMediaListTranslatorTest {

    private static final MovieFilter.Condition UNWATCHED =
            new MovieFilter.Condition(MovieFilterField.WATCHED, FilterOperator.IS, "false");
    private static final MovieFilter.Condition WATCHED =
            new MovieFilter.Condition(MovieFilterField.WATCHED, FilterOperator.IS, "true");

    @Test
    void translatesTheUnwatchedListOfTheUser() {
        assertThat(EmberMediaListTranslator.translate("CREATE VIEW 'movie-tiktak_unviewed' AS SELECT * FROM movielist "
                + "WHERE movielist.Playcount IS NULL"))
                .contains(new MovieFilter(null, true, List.of(UNWATCHED)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Playcount = 0", "(playcount < 1)", "\"Playcount\" <= '0'"})
    void understandsOtherWaysToSayUnwatched(String condition) {
        assertThat(EmberMediaListTranslator.translate("SELECT * FROM movielist WHERE " + condition + ";"))
                .contains(new MovieFilter(null, true, List.of(UNWATCHED)));
    }

    @Test
    void combinesConditionsAndTranslatesAListWithoutConditions() {
        assertThat(EmberMediaListTranslator.translate("SELECT * FROM movielist WHERE Playcount > 0 AND Playcount IS "
                + "NOT NULL")).contains(new MovieFilter(null, true, List.of(WATCHED, WATCHED)));
        assertThat(EmberMediaListTranslator.translate("SELECT * FROM movielist")).contains(MovieFilter.ALL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Title LIKE '%Bond%'", "Playcount IS NULL OR Year > 2000", "Playcount = 2",
        "Playcount IS NULL AND Year > 2000"})
    void leavesConditionsItCannotTranslate(String condition) {
        assertThat(EmberMediaListTranslator.translate("SELECT * FROM movielist WHERE " + condition)).isEmpty();
    }
}
