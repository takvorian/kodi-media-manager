package de.tiktak.kmm.scanner.naming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tiktak.kmm.i18n.LocalizedText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class NamePatternTest {

    private static final Set<String> PLACEHOLDERS = Set.of("title", "year", "source", "edition", "show", "season",
            "episode");

    private final Map<String, String> values = new HashMap<>(Map.of("title", "Heat", "year", "1995"));

    @Test
    void replacesPlaceholdersAndKeepsText() {
        assertThat(render("{title} ({year})")).containsExactly("Heat (1995)");
    }

    @Test
    void dropsOptionalPartsWithAnEmptyPlaceholder() {
        assertThat(render("{title}[ ({year})][.{source}]")).containsExactly("Heat (1995)");
        values.put("source", "bluray");
        assertThat(render("{title}[.{source}]")).containsExactly("Heat.bluray");
        assertThat(render("{title}[ - {edition} ({year})]")).as("one empty placeholder is enough")
                .containsExactly("Heat");
    }

    @Test
    void leavesOutEmptyPlaceholdersOutsideOptionalParts() {
        assertThat(render("{title} {edition}")).containsExactly("Heat");
    }

    @Test
    void splitsFoldersAndDropsEmptyOnes() {
        assertThat(render("{title:first}/{title} ({year})")).containsExactly("H", "Heat (1995)");
        assertThat(render("{edition}\\{title}")).containsExactly("Heat");
        assertThat(NamePattern.parse("{title:first}/{title}", PLACEHOLDERS).hasFolders()).isTrue();
        assertThat(NamePattern.parse("{title}[/{year}]", PLACEHOLDERS).hasFolders()).isTrue();
        assertThat(NamePattern.parse("{title}", PLACEHOLDERS).hasFolders()).isFalse();
    }

    @Test
    void valuesNeverSplitFoldersAndAreCleaned() {
        values.put("title", "AC/DC: Live at River Plate?");

        assertThat(render("{title}")).containsExactly("AC-DC - Live at River Plate");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{season:2}|7|07",
        "{season:3}|7|007",
        "{season:2}|123|123",
        "{title:first}|heat|H",
        "{title:first}|12 Monkeys|#",
        "{title:first}|Ärger|Ä",
        "{title:sort}|The Matrix|Matrix, The",
        "{title:sort}|Die Hard|Hard, Die",
        "{title:sort}|Theater|Theater",
        "{title:sort:first}|The Matrix|M",
        "{title:upper}|Heat|HEAT",
        "{title:lower}|Heat|heat"
    })
    void formatsValues(String pattern, String value, String expected) {
        values.put("title", value);
        values.put("season", value);

        assertThat(render(pattern)).containsExactly(expected);
    }

    @Test
    void escapesBracesAndBrackets() {
        assertThat(render("{title} [[{year}]] {{x}}")).containsExactly("Heat [1995] {x}");
    }

    @Test
    void rendersTheUsersEpisodePattern() {
        values.putAll(Map.of("show", "Lost", "season", "1", "episode", "1"));

        assertThat(render("{show} - S{season:2}E{episode:2}[ - {title}]")).containsExactly("Lost - S01E01 - Heat");
        values.remove("title");
        assertThat(render("Staffel {season}/{show} - S{season:2}E{episode:2}[ - {title}]"))
                .containsExactly("Staffel 1", "Lost - S01E01");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "''|The pattern is empty.",
        "{title|“{” without matching “}” (position 1).",
        "{title {year}|“{” without matching “}” (position 1).",
        "title}|“}” without matching “{” (position 6).",
        "[{title}|“[” without matching “]” (position 1).",
        "{title}]|“]” without matching “[” (position 8).",
        "[a[b]]|Optional parts cannot be nested (position 3).",
        "{titel}|Unknown placeholder “{titel}” (position 1).",
        "{title:fett}|Unknown format “fett” for “{title}” (position 1)."
    })
    void rejectsInvalidPatterns(String pattern, String message) {
        assertThatThrownBy(() -> NamePattern.parse(pattern, PLACEHOLDERS))
                .isInstanceOf(NamePatternException.class).hasMessage(message);
    }

    @Test
    void namesTheProblemAndItsPositionAsTranslatableText() {
        assertThatThrownBy(() -> NamePattern.parse("{titel}", PLACEHOLDERS))
                .isInstanceOfSatisfying(NamePatternException.class, e -> assertThat(e.localizedText()).isEqualTo(
                        LocalizedText.of("scanner.pattern.invalid")
                                .with("problem", LocalizedText.of("scanner.pattern.unknownPlaceholder")
                                        .with("placeholder", "{titel}"))
                                .with("position", 1)));
    }

    @Test
    void keepsItsText() {
        NamePattern pattern = NamePattern.parse("{title}", PLACEHOLDERS);

        assertThat(pattern.text()).isEqualTo("{title}");
        assertThat(pattern).hasToString("{title}");
    }

    private List<String> render(String pattern) {
        return NamePattern.parse(pattern, PLACEHOLDERS).render(values::get);
    }
}
