package de.tiktak.kmm.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReleaseInfoServiceTest {

    @Test
    void readsTheVersionsFromTheChangelog() {
        ReleaseInfoService service = new ReleaseInfoService("changelog-test.md");

        assertThat(service.currentVersion()).isEqualTo("v0.2");
        assertThat(service.changelog()).containsExactly(
                new ChangelogVersion("v0.2", "2026-11-02", List.of("Ä und Ö im Betreff (abc1234)", "Zweite (def5678)")),
                new ChangelogVersion("v0.1", "2026-10-30", List.of("Erste (0123abc)")));
    }

    @Test
    void isADevelopmentVersionWithoutAChangelog() {
        ReleaseInfoService service = new ReleaseInfoService("gibt-es-nicht.md");

        assertThat(service.currentVersion()).isEqualTo(ReleaseInfoService.DEV_VERSION);
        assertThat(service.changelog()).isEmpty();
    }

    @Test
    void ignoresLinesOutsideOfAVersion() {
        assertThat(ReleaseInfoService.parse(List.of("# Changelog", "", "- verwaist", "Text"))).isEmpty();
    }

    @Test
    void keepsAVersionWithoutChanges() {
        assertThat(ReleaseInfoService.parse(List.of("## v0.3 - 2026-11-03", "", "## v0.2 - 2026-11-02", "- Eine")))
                .containsExactly(
                        new ChangelogVersion("v0.3", "2026-11-03", List.of()),
                        new ChangelogVersion("v0.2", "2026-11-02", List.of("Eine")));
    }

    @Test
    void acceptsAHeadingWithoutDate() {
        assertThat(ReleaseInfoService.parse(List.of("## v0.1", "- Eine")))
                .containsExactly(new ChangelogVersion("v0.1", "", List.of("Eine")));
    }

    @Test
    void keepsSeparatorsInsideOfAChange() {
        assertThat(ReleaseInfoService.parse(List.of("## v0.1 - 2026-10-30", "- Fix - mit Strich ## und Raute")))
                .containsExactly(new ChangelogVersion("v0.1", "2026-10-30", List.of("Fix - mit Strich ## und Raute")));
    }
}
