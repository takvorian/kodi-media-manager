package de.tiktak.kmm.scanner.naming;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CompanionNamesTest {

    @ParameterizedTest
    @CsvSource({
            "Heat.nfo, true",
            "heat-poster.jpg, true",
            "Heat_cd2.avi, true",
            "'Heat de.srt', true",
            "Heat, true",
            "Heater.mkv, false",
            "Hea.mkv, false",
            "Heat2.nfo, false"})
    void belongsByNameFollowedByASeparator(String fileName, boolean belongs) {
        assertThat(CompanionNames.belongsTo(fileName, "Heat")).isEqualTo(belongs);
    }

    @Test
    void leavesFilesOfLongerNamesToTheirVideo() {
        assertThat(CompanionNames.belongsOnlyTo("Heat 2.nfo", "Heat", List.of("Heat 2"))).isFalse();
        assertThat(CompanionNames.belongsOnlyTo("Heat.nfo", "Heat", List.of("Heat 2"))).isTrue();
        assertThat(CompanionNames.belongsOnlyTo("Heat 2.nfo", "Heat 2", List.of("Heat"))).isTrue();
    }

    @Test
    void cutsTheExtension() {
        assertThat(CompanionNames.baseName(Path.of("a", "Heat.1995.mkv"))).isEqualTo("Heat.1995");
        assertThat(CompanionNames.baseName(Path.of("a", ".hidden"))).isEqualTo(".hidden");
        assertThat(CompanionNames.baseName(Path.of("a", "README"))).isEqualTo("README");
    }
}
