package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScrapeLanguageTest {

    @Test
    void appliesOnlyWithinItsBody() {
        assertThat(ScrapeLanguage.current()).isNull();

        assertThat(ScrapeLanguage.with("fr-FR", ScrapeLanguage::current)).isEqualTo("fr-FR");

        assertThat(ScrapeLanguage.current()).isNull();
    }

    @Test
    void leavesTheGeneralSettingsInChargeWithoutALanguage() {
        assertThat(ScrapeLanguage.with(null, ScrapeLanguage::current)).isNull();
    }

    @Test
    void separatesCachedAnswersByLanguage() {
        assertThat(ScrapeLanguage.cacheKey()).isEmpty();
        assertThat(ScrapeLanguage.with("fr-FR", ScrapeLanguage::cacheKey)).isEqualTo("fr-FR");
    }

    @Test
    void derivesThePreferredImageLanguage() {
        assertThat(ScrapeLanguage.preferredImageLanguage("de")).isEqualTo("de");
        assertThat(ScrapeLanguage.with("fr-FR", () -> ScrapeLanguage.preferredImageLanguage("de"))).isEqualTo("fr");
        assertThat(ScrapeLanguage.with("en", () -> ScrapeLanguage.preferredImageLanguage("de"))).isEqualTo("en");
    }
}
