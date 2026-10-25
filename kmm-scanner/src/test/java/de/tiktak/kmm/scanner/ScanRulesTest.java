package de.tiktak.kmm.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ScanRulesTest {

    @Test
    void defaultsContainCommonVideoExtensionsAndExtrasFolders() {
        ScanRules rules = ScanRules.defaults();

        assertThat(rules.isVideoExtension("mkv")).isTrue();
        assertThat(rules.isVideoExtension("ISO")).isTrue();
        assertThat(rules.isVideoExtension("nfo")).isFalse();
        assertThat(rules.isIgnoredDirectory("Extras")).isTrue();
        assertThat(rules.isIgnoredDirectory("Heat (1995)")).isFalse();
    }

    @Test
    void normalizesCustomValuesToLowerCase() {
        ScanRules rules = new ScanRules(Set.of("MKV"), Set.of("Bonus"));

        assertThat(rules.videoExtensions()).containsExactly("mkv");
        assertThat(rules.isIgnoredDirectory("BONUS")).isTrue();
    }

    @Test
    void recognizesExcludedDirectoriesAndTheirSubdirectories() {
        Path excluded = Path.of("library", "Privat").toAbsolutePath();
        ScanRules rules = new ScanRules(Set.of("mkv"), Set.of(), Set.of(excluded.resolve("..").resolve("Privat")));

        assertThat(rules.isExcludedDirectory(excluded)).isTrue();
        assertThat(rules.isExcludedDirectory(excluded.resolve("Urlaub"))).isTrue();
        assertThat(rules.isExcludedDirectory(excluded.resolveSibling("Privatfilme"))).isFalse();
    }
}
