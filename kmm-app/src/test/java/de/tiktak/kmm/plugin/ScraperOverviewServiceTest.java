package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.ScraperOverview.Capability;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.SettingDefinition;
import de.tiktak.kmm.plugin.api.TvShowScraper;
import de.tiktak.kmm.settings.PluginSettingService;

@ExtendWith(MockitoExtension.class)
class ScraperOverviewServiceTest {

    private static final SettingDefinition API_KEY = SettingDefinition.secret("api-key", "API key");
    private static final LocalizedText MISSING_KEY = LocalizedText.of("scraper.apiKeyMissing");

    @Mock
    private PluginManager pluginManager;

    @Mock
    private PluginService pluginService;

    @Mock
    private PluginSettingService pluginSettingService;

    private ScraperOverviewService service;

    @BeforeEach
    void setUp() {
        PluginProperties properties = new PluginProperties(Path.of("plugins"), true,
                Map.of("tvdb", Map.of("api-key", "aus-der-umgebung")));
        service = new ScraperOverviewService(pluginManager, pluginService, properties, pluginSettingService);
    }

    @Test
    void listsWhatEachPluginDeliversAndKeepsSwitchedOffOnes() {
        PluginWrapper tvdb = plugin("tvdb", PluginState.STARTED);
        PluginWrapper trakt = plugin("trakt", PluginState.DISABLED);
        PluginWrapper library = plugin("library", PluginState.STARTED);
        when(pluginManager.getPlugins()).thenReturn(List.of(tvdb, trakt, library));
        TvShowScraper shows = scraper(TvShowScraper.class, Set.of(MediaType.TV_SHOW), Optional.of(MISSING_KEY));
        ImageScraper images = scraper(ImageScraper.class, Set.of(MediaType.TV_SHOW, MediaType.TV_SEASON),
                Optional.empty());
        when(pluginManager.getExtensions(Scraper.class, "tvdb")).thenReturn(List.of(shows, images));
        when(pluginManager.getExtensions(Scraper.class, "library")).thenReturn(List.of());

        assertThat(service.scrapers()).satisfiesExactly(
                first -> {
                    assertThat(first.id()).isEqualTo("tvdb");
                    assertThat(first.displayName()).isEqualTo("TheTVDB");
                    assertThat(first.enabled()).isTrue();
                    assertThat(first.missingConfiguration()).isEqualTo(MISSING_KEY);
                    assertThat(first.capabilities()).containsExactly(
                            new Capability(ScraperKind.METADATA, Set.of(MediaType.TV_SHOW)),
                            new Capability(ScraperKind.ARTWORK, Set.of(MediaType.TV_SHOW, MediaType.TV_SEASON)));
                },
                second -> {
                    assertThat(second.id()).isEqualTo("trakt");
                    assertThat(second.enabled()).isFalse();
                    assertThat(second.capabilities()).isEmpty();
                });
    }

    @Test
    void showsTheSettingsEachDeclaredOnceWithTheConfiguration() {
        PluginWrapper tvdb = plugin("tvdb", PluginState.STARTED);
        when(pluginManager.getPlugin("tvdb")).thenReturn(tvdb);
        TvShowScraper shows = scraper(TvShowScraper.class, Set.of(MediaType.TV_SHOW), Optional.empty());
        ImageScraper images = scraper(ImageScraper.class, Set.of(MediaType.TV_SHOW), Optional.empty());
        when(shows.settingDefinitions()).thenReturn(List.of(API_KEY));
        when(images.settingDefinitions()).thenReturn(List.of(API_KEY));
        when(pluginManager.getExtensions(Scraper.class, "tvdb")).thenReturn(List.of(shows, images));

        service.changeSettings("tvdb", Map.of("api-key", "neu"));

        verify(pluginSettingService).update("tvdb", List.of(API_KEY), Map.of("api-key", "neu"));
        verify(pluginSettingService).view(eq("tvdb"), eq(List.of(API_KEY)), eq(Map.of("api-key", "aus-der-umgebung")));
    }

    @Test
    void offersTheSettingsOfTheStartedPluginsForSeeding() {
        PluginWrapper tvdb = plugin("tvdb", PluginState.STARTED);
        when(pluginManager.getStartedPlugins()).thenReturn(List.of(tvdb));
        TvShowScraper shows = scraper(TvShowScraper.class, Set.of(MediaType.TV_SHOW), Optional.empty());
        when(shows.settingDefinitions()).thenReturn(List.of(API_KEY));
        when(pluginManager.getExtensions(Scraper.class, "tvdb")).thenReturn(List.of(shows));

        new PluginSettingSeeder(new PluginProperties(Path.of("plugins"), true,
                Map.of("tvdb", Map.of("api-key", "aus-der-umgebung"))), service, pluginSettingService).run(null);

        verify(pluginSettingService).seedIfMissing("tvdb", "api-key", "aus-der-umgebung");
    }

    @Test
    void rejectsUnknownPlugins() {
        assertThatThrownBy(() -> service.details("gibt-es-nicht")).isInstanceOf(PluginNotFoundException.class);
    }

    private static PluginWrapper plugin(String id, PluginState state) {
        PluginWrapper plugin = mock(PluginWrapper.class);
        PluginDescriptor descriptor = mock(PluginDescriptor.class);
        lenient().when(plugin.getPluginId()).thenReturn(id);
        lenient().when(plugin.getPluginState()).thenReturn(state);
        lenient().when(plugin.getDescriptor()).thenReturn(descriptor);
        lenient().when(descriptor.getVersion()).thenReturn("1.0");
        return plugin;
    }

    private static <T extends Scraper> T scraper(Class<T> type, Set<MediaType> mediaTypes,
            Optional<LocalizedText> missing) {
        T scraper = mock(type);
        lenient().when(scraper.displayName()).thenReturn("TheTVDB");
        lenient().when(scraper.supportedMediaTypes()).thenReturn(mediaTypes);
        lenient().when(scraper.missingConfiguration()).thenReturn(missing);
        lenient().when(scraper.attribution()).thenReturn(Optional.empty());
        return scraper;
    }
}
