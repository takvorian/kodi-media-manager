package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;
import org.junit.jupiter.api.Test;

class EmberImportProgressTest {

    @Test
    void countsEverythingButLimitsTheLists() {
        var progress = new EmberImportProgress(true);
        for (int index = 0; index < 150; index++) {
            progress.movieImported(index % 2 == 0);
            progress.nfoMissing("Film " + index);
            progress.sourceSkipped(LocalizedText.literal("Quelle " + index));
            progress.warn(LocalizedText.literal("Hinweis " + index));
        }
        progress.sourceImported(true);
        progress.sourceImported(false);
        progress.movieSkipped();
        progress.settingsChanged(List.of(LocalizedText.literal("Sprache")));
        progress.kodiHostImported(true);
        progress.kodiHostImported(false);
        progress.kodiHostImported(false);

        EmberImportReport report = progress.toReport();

        assertThat(report.dryRun()).isTrue();
        assertThat(report.moviesCreated()).isEqualTo(75);
        assertThat(report.moviesUpdated()).isEqualTo(75);
        assertThat(report.moviesSkipped()).isEqualTo(1);
        assertThat(report.moviesWithoutNfo()).isEqualTo(150);
        assertThat(report.sourcesCreated()).isEqualTo(1);
        assertThat(report.sourcesReused()).isEqualTo(1);
        assertThat(report.kodiHostsCreated()).isEqualTo(1);
        assertThat(report.kodiHostsUpdated()).isEqualTo(2);
        assertThat(report.missingFiles()).hasSize(EmberImportProgress.MAX_LISTED_ENTRIES);
        assertThat(report.skippedSources()).hasSize(EmberImportProgress.MAX_LISTED_ENTRIES);
        assertThat(report.warnings()).hasSize(EmberImportProgress.MAX_LISTED_ENTRIES);
        assertThat(report.settingsChanges()).containsExactly(LocalizedText.literal("Sprache"));
    }

    @Test
    void countsShowsAndEpisodes() {
        var progress = new EmberImportProgress(false);
        progress.showImported(true);
        progress.showImported(false);
        progress.showSkipped();
        progress.episodeImported(true);
        progress.episodeImported(true);
        progress.episodeImported(false);
        progress.episodeSkipped();
        progress.episodesMissing(4);

        assertThat(progress.toReport().tvShows()).isEqualTo(new EmberImportReport.TvShows(1, 1, 1, 2, 1, 1, 4));
    }

    @Test
    void listsEachWarningOnlyOnce() {
        var progress = new EmberImportProgress(false);
        LocalizedText unreachable = LocalizedText.of("migration.warning.sourceUnreachable").with("name", "Filme");
        progress.warn(unreachable);
        progress.warn(LocalizedText.of("migration.warning.sourceUnreachable").with("name", "Filme"));

        assertThat(progress.toReport().warnings()).containsExactly(unreachable);
    }
}
