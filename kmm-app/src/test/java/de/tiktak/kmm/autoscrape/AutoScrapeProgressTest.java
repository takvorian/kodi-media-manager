package de.tiktak.kmm.autoscrape;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutoScrapeProgressTest {

    @Test
    void limitsListedEntriesButKeepsCounting() {
        var progress = new AutoScrapeProgress(500);
        for (int index = 0; index < 150; index++) {
            progress.unmatched(index, "Film " + index);
            progress.failed("Film " + index, "Fehler");
            progress.warn("Hinweis " + index);
        }

        AutoScrapeResult result = progress.toResult();

        assertThat(result.unmatched()).isEqualTo(150);
        assertThat(result.unmatchedEntries()).hasSize(AutoScrapeProgress.MAX_LISTED_ENTRIES);
        assertThat(result.failures()).hasSize(AutoScrapeProgress.MAX_LISTED_ENTRIES);
        assertThat(result.warnings()).hasSize(AutoScrapeProgress.MAX_LISTED_ENTRIES);
        assertThat(result.movies()).isEqualTo(500);
    }

    @Test
    void listsEachWarningOnlyOnce() {
        var progress = new AutoScrapeProgress(2);
        progress.warn("fanart.tv: Key fehlt");
        progress.warn("fanart.tv: Key fehlt");

        assertThat(progress.toResult().warnings()).containsExactly("fanart.tv: Key fehlt");
    }
}
