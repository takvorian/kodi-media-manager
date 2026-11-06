package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.migration.ember.settings.EmberMovieSetSettings;
import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberRenameSettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.migration.ember.settings.EmberTvSettings;
import de.tiktak.kmm.settings.EpisodeOrdering;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.MediaInfoSettings;
import de.tiktak.kmm.settings.MovieSetSettings;
import de.tiktak.kmm.settings.ProxySettings;
import de.tiktak.kmm.settings.RenameSettings;
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
        assertThat(english(imported)).containsExactly("Language of the TV show metadata: de-DE",
                "Preferred image language for TV shows: de", "Episode order: Absolute");
    }

    @Test
    void takesOverTheMovieSetSettingsWithTranslatedFolders() {
        var ember = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null, null, null, null,
                new EmberMovieSetSettings(true, "D:\\Filme\\_filmsets", false, null, true));

        var imported = EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of(),
                path -> Optional.of(path.replace("D:\\Filme", "/media/filme").replace('\\', '/')));

        assertThat(imported.settings().movieSets())
                .isEqualTo(new MovieSetSettings(true, "/media/filme/_filmsets", false, null, true));
        assertThat(english(imported)).containsExactly(
                "Folder for “Set images in the flat scheme”: /media/filme/_filmsets",
                "Set images in the flat scheme: yes", "Set images in Kodi's set folders: no",
                "Assign movies to their movie set when scraping: yes");
        assertThat(imported.changes().getFirst()).isEqualTo(LocalizedText.of("migration.settings.folder")
                .with("label", LocalizedText.of("migration.settings.label.flatImages"))
                .with("folder", "/media/filme/_filmsets"));
    }

    @Test
    void keepsAMovieSetImageStorageOffWhenItsFolderCannotBeTranslated() {
        var ember = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null, null, null, null,
                new EmberMovieSetSettings(null, null, true, "\\\\nas\\sets", null));

        var imported = EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of(), path -> Optional.empty());

        assertThat(imported.settings().movieSets()).isEqualTo(MovieSetSettings.DEFAULTS);
        assertThat(english(imported)).containsExactly(
                "Folder for “Set images in Kodi's set folders” not imported: “\\\\nas\\sets” matches no path mapping.",
                "“Set images in Kodi's set folders” stays off because the folder is missing.");
    }

    @Test
    void takesOverWhetherEmberReadsMediaInfoWhileScraping() {
        var ember = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null, null, false);

        var imported = EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of());

        assertThat(imported.settings().mediaInfo()).isEqualTo(new MediaInfoSettings("ffprobe", false, true));
        assertThat(english(imported)).containsExactly("Read technical details when scraping: no");
        var withoutValue = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null, null);
        assertThat(EmberSettingsMapper.map(CURRENT, Optional.of(withoutValue), List.of()).settings().mediaInfo())
                .isEqualTo(MediaInfoSettings.DEFAULTS);
    }

    @Test
    void translatesTheRenamePatternsAndKeepsThoseWithoutCounterpart() {
        var ember = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null, null, null,
                new EmberRenameSettings("$T {($Y)}", "$F", null, "Staffel $K1_?", "$Z - $W2_S?2E?{ - $T}", false,
                        true, null, null, false));

        var imported = EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of());

        assertThat(imported.settings().rename()).isEqualTo(new RenameSettings("{title} [({year})]",
                RenameSettings.DEFAULTS.movieFile(), RenameSettings.DEFAULTS.tvShowFolder(), "Staffel {season}",
                "{show} - S{season:2}E{episode:2}[ - {title}]", false, true, false, false, false));
        assertThat(english(imported)).containsExactly(
                "Pattern for “Movie folders”: {title} [({year})] (Ember: $T {($Y)})",
                "Pattern for “Movie files” not imported: “$F” cannot be translated, “{title}[.{source}]” remains.",
                "Pattern for “Season folders”: Staffel {season} (Ember: Staffel $K1_?)",
                "Pattern for “Episode files”: {show} - S{season:2}E{episode:2}[ - {title}] (Ember: $Z - $W2_S?2E?{ - $T})",
                "Rename movies after editing: no",
                "Rename movies after scraping: yes",
                "Rename new episodes when scanning: no");
        assertThat(EmberSettingsMapper.map(CURRENT, Optional.of(ember.withRename(null)), List.of()).settings()
                .rename()).isEqualTo(RenameSettings.DEFAULTS);
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
        assertThat(english(imported)).containsExactly("Language of the metadata: de-DE",
                "Preferred image language: de", "Video file extensions: mkv, avi",
                "Proxy: proxy.local:3128 (please enter the password in the settings)",
                "Excluded folders: /media/privat");
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
        assertThat(english(imported)).containsExactly("Proxy: proxy.local:8080");
    }

    @Test
    void importsOnlyExcludedPathsWithoutSettingsFile() {
        var imported = EmberSettingsMapper.map(CURRENT, Optional.empty(), List.of("/media/privat"));

        assertThat(imported.settings().excludedPaths()).containsExactly("/media/tmp", "/media/privat");
        assertThat(imported.settings().metadataLanguage()).isEqualTo("en-US");
    }

    private static List<String> english(EmberSettingsMapper.SettingsImport imported) {
        return imported.changes().stream().map(Messages::english).toList();
    }

    @Test
    void ignoresUnparsableProxyUri() {
        var ember = new EmberSettings(1, null, List.of(), List.of(),
                Optional.of(new EmberProxySettings("http://[kaputt", 3128, null)), null, null);

        assertThat(EmberSettingsMapper.map(CURRENT, Optional.of(ember), List.of()).settings().proxy()).isNull();
    }
}
