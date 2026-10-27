package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.migration.ember.settings.EmberTvSettings;
import de.tiktak.kmm.settings.EpisodeOrdering;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.MediaInfoSettings;
import de.tiktak.kmm.settings.ProxySettings;
import de.tiktak.kmm.settings.TvSettings;

class EmberSettingsMapperTest {

    private static final KmmSettings CURRENT = new KmmSettings("en-US", "en", List.of("mkv"), List.of("/media/tmp"),
            null);

    @Test
    void takesOverEmberTvSettings() {
        var ember = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null,
                new EmberTvSettings("de-DE", "de", "Absolute"));

        var imported = EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of());

        assertThat(imported.settings().tv()).isEqualTo(new TvSettings("de-DE", "de", EpisodeOrdering.ABSOLUTE));
        assertThat(imported.changes()).containsExactly("Sprache der Serien-Metadaten: de-DE",
                "Bevorzugte Bildsprache für Serien: de", "Episodenreihenfolge: Absolute");
    }

    @Test
    void takesOverWhetherEmberReadsMediaInfoWhileScraping() {
        var ember = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null, null, false);

        var imported = EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of());

        assertThat(imported.settings().mediaInfo()).isEqualTo(new MediaInfoSettings("ffprobe", false, true));
        assertThat(imported.changes()).containsExactly("Technische Angaben beim Scrapen auslesen: nein");
        var withoutValue = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null, null);
        assertThat(EmberSettingsMapper.map(CURRENT, Optional.of(withoutValue), List.of()).settings().mediaInfo())
                .isEqualTo(MediaInfoSettings.DEFAULTS);
    }

    @Test
    void keepsTheTvSettingsForUnsupportedEmberValues() {
        var ember = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null,
                new EmberTvSettings("deutsch", null, "DayOfYear"));

        assertThat(EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of()).settings().tv())
                .isEqualTo(CURRENT.tv());
    }

    @Test
    void takesOverValidEmberSettings() {
        var ember = new EmberSettings(1, "German_(de)", List.of(".MKV", ".avi", ".dvr-ms", ".mkv"), List.of(),
                Optional.of(new EmberProxySettings("http://proxy.local", 3128, "user")), "de-DE", "de");

        var imported = EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of("/media/privat"));

        assertThat(imported.settings()).isEqualTo(new KmmSettings("de-DE", "de", List.of("mkv", "avi"),
                List.of("/media/tmp", "/media/privat"), new ProxySettings("proxy.local", 3128, "user", null),
                CURRENT.tv()));
        assertThat(imported.changes()).containsExactly("Sprache der Metadaten: de-DE", "Bevorzugte Bildsprache: de",
                "Video-Dateiendungen: mkv, avi",
                "Proxy: proxy.local:3128 (Passwort bitte in den Einstellungen eingeben)",
                "Ausgeschlossene Ordner: /media/privat");
    }

    @Test
    void keepsCurrentValuesForMissingOrInvalidEmberSettings() {
        var ember = new EmberSettings(1, null, List.of("kaputt!"), List.of(),
                Optional.of(new EmberProxySettings("http://", -1, null)), "Deutsch", "deu");

        var imported = EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of("/media/tmp"));

        assertThat(imported.settings()).isEqualTo(CURRENT);
        assertThat(imported.changes()).isEmpty();
    }

    @Test
    void acceptsProxyWithoutSchemeAndUsesDefaultPort() {
        var ember = new EmberSettings(1, null, List.of(), List.of(),
                Optional.of(new EmberProxySettings("proxy.local", -1, null)), null, null);

        var imported = EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of());

        assertThat(imported.settings().proxy()).isEqualTo(new ProxySettings("proxy.local", 8080, null, null));
        assertThat(imported.changes()).containsExactly("Proxy: proxy.local:8080");
    }

    @Test
    void importsOnlyExcludedPathsWithoutSettingsFile() {
        var imported = EmberSettingsMapper.map(CURRENT, Optional.empty(), List.of("/media/privat"));

        assertThat(imported.settings().excludedPaths()).containsExactly("/media/tmp", "/media/privat");
        assertThat(imported.settings().metadataLanguage()).isEqualTo("en-US");
    }

    @Test
    void ignoresUnparsableProxyUri() {
        var ember = new EmberSettings(1, null, List.of(), List.of(),
                Optional.of(new EmberProxySettings("http://[kaputt", 3128, null)), null, null);

        assertThat(EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of()).settings().proxy()).isNull();
    }
}
