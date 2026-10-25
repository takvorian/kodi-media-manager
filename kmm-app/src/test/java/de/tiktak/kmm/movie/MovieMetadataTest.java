package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class MovieMetadataTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void trimsTextsAndTurnsBlanksIntoNull() {
        var metadata = new MovieMetadata("  Heat ", " ", null, null, null, "\n", null, null, null, null,
                Arrays.asList(" Krimi ", "", null, "Krimi", "Drama"), " tt0113277 ", "");

        assertThat(metadata.title()).isEqualTo("Heat");
        assertThat(metadata.originalTitle()).isNull();
        assertThat(metadata.plot()).isNull();
        assertThat(metadata.genres()).containsExactly("Krimi", "Drama");
        assertThat(metadata.imdbId()).isEqualTo("tt0113277");
        assertThat(metadata.tmdbId()).isNull();
    }

    @Test
    void acceptsValidMetadata() {
        assertThat(validator.validate(metadata("Heat", 1995, "tt0113277", "949"))).isEmpty();
    }

    @Test
    void requiresTitle() {
        assertThat(violatedFields(metadata(" ", null, null, null))).containsExactly("title");
    }

    @ParameterizedTest
    @ValueSource(ints = {1869, 2201})
    void rejectsImplausibleYear(int year) {
        assertThat(violatedFields(metadata("Heat", year, null, null))).containsExactly("year");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0113277", "tt12", "nm0000199"})
    void rejectsInvalidImdbId(String imdbId) {
        assertThat(violatedFields(metadata("Heat", null, imdbId, null))).containsExactly("imdbId");
    }

    @Test
    void rejectsInvalidTmdbId() {
        assertThat(violatedFields(metadata("Heat", null, null, "abc"))).containsExactly("tmdbId");
    }

    @Test
    void requiresGenreList() {
        var metadata = new MovieMetadata("Heat", null, null, null, null, null, null, null, null, null, null, null,
                null);

        assertThat(violatedFields(metadata)).containsExactly("genres");
    }

    private static Set<String> violatedFields(MovieMetadata metadata) {
        return validator.validate(metadata).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static MovieMetadata metadata(String title, Integer year, String imdbId, String tmdbId) {
        return new MovieMetadata(title, null, null, year, null, null, null, null, 120, null, List.of("Krimi"),
                imdbId, tmdbId);
    }

    @Test
    void cutsForeignDataToTheLimitsOfTheDatabase() {
        var foreign = new MovieMetadata("x".repeat(600), null, null, 1500, null, "p".repeat(30_000), null, null, -5,
                "m".repeat(100), java.util.stream.IntStream.range(0, 60).mapToObj(index -> "Genre " + index).toList(),
                "0113277", "949a");

        MovieMetadata limited = foreign.withinLimits();

        assertThat(limited.title()).hasSize(MovieMetadata.MAX_TITLE_LENGTH);
        assertThat(limited.plot()).hasSize(MovieMetadata.MAX_PLOT_LENGTH);
        assertThat(limited.mpaa()).hasSize(MovieMetadata.MAX_MPAA_LENGTH);
        assertThat(limited.genres()).hasSize(MovieMetadata.MAX_GENRES);
        assertThat(limited.year()).isNull();
        assertThat(limited.runtime()).isNull();
        assertThat(limited.imdbId()).isNull();
        assertThat(limited.tmdbId()).isNull();
        assertThat(validator.validate(limited)).isEmpty();
    }

    @Test
    void keepsValidValuesWithinLimits() {
        var valid = new MovieMetadata("Heat", null, null, 1995, null, null, null, null, 170, "FSK 16", List.of("Krimi"),
                "tt0113277", "949");

        assertThat(valid.withinLimits()).isEqualTo(valid);
    }
}
