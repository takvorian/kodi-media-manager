package de.tiktak.kmm.i18n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MessageTemplateTest {

    private static String format(String pattern, Map<String, Object> args) {
        return MessageTemplate.parse(pattern).format(args, text -> "<" + text.key() + ">");
    }

    @Test
    void keepsPlainText() {
        assertThat(format("Plain text.", Map.of())).isEqualTo("Plain text.");
    }

    @Test
    void insertsNamedArguments() {
        assertThat(format("{file} cannot be written to {folder}.", Map.of("file", "a.nfo", "folder", "/x")))
                .isEqualTo("a.nfo cannot be written to /x.");
    }

    @Test
    void keepsMissingArgumentsVisible() {
        assertThat(format("Hello {name}", Map.of())).isEqualTo("Hello {name}");
    }

    @Test
    void rendersNullAsEmptyText() {
        assertThat(format("[{value}]", java.util.Collections.singletonMap("value", null))).isEqualTo("[]");
    }

    @Test
    void rendersNestedTextsWithTheGivenRenderer() {
        assertThat(format("{job} is already running.", Map.of("job", LocalizedText.of("job.scan"))))
                .isEqualTo("<job.scan> is already running.");
    }

    @ParameterizedTest
    @CsvSource({"0, no movies", "1, 1 movie", "2, 2 movies", "1170, 1170 movies"})
    void choosesThePluralForm(int count, String expected) {
        assertThat(format("{count, plural, =0 {no movies} one {# movie} other {# movies}}", Map.of("count", count)))
                .isEqualTo(expected);
    }

    @Test
    void acceptsNumbersAsTextInPlurals() {
        assertThat(format("{n, plural, one {# file} other {# files}}", Map.of("n", "1"))).isEqualTo("1 file");
    }

    @Test
    void usesOtherForFractionsAndNonNumbers() {
        String pattern = "{n, plural, one {one} other {other}}";

        assertThat(format(pattern, Map.of("n", 1.5))).isEqualTo("other");
        assertThat(format(pattern, Map.of("n", "many"))).isEqualTo("other");
        assertThat(format(pattern, Map.of())).isEqualTo("other");
    }

    @Test
    void nestsArgumentsInsidePlurals() {
        assertThat(format("{count, plural, one {# movie in {source}} other {# movies in {source}}}",
                Map.of("count", 3, "source", "Movies"))).isEqualTo("3 movies in Movies");
    }

    @Test
    void choosesBySelectValue() {
        String pattern = "{kind, select, movie {the movie} show {the TV show} other {the entry}}";

        assertThat(format(pattern, Map.of("kind", "show"))).isEqualTo("the TV show");
        assertThat(format(pattern, Map.of("kind", "episode"))).isEqualTo("the entry");
    }

    @Test
    void quotesSpecialCharactersWithApostrophes() {
        assertThat(format("Use '{title}' ('{year}') and don't worry", Map.of()))
                .isEqualTo("Use {title} ({year}) and don't worry");
        assertThat(format("It''s '{'quoted''s'}'", Map.of())).isEqualTo("It's {quoted's}");
    }

    @Test
    void quotesThePoundSignOnlyInsidePlurals() {
        assertThat(format("#1 {n, plural, other {'#' #}}", Map.of("n", 2))).isEqualTo("#1 # 2");
    }

    @Test
    void keepsATrailingApostrophe() {
        assertThat(format("movies'", Map.of())).isEqualTo("movies'");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Hello {name", "Hello }", "{}", "{n, number}", "{n, plural, one {x}}", "{n, plural, other x}",
            "{n, plural, other {x}"})
    void rejectsMalformedPatterns(String pattern) {
        assertThatIllegalArgumentException().isThrownBy(() -> MessageTemplate.parse(pattern));
    }
}
