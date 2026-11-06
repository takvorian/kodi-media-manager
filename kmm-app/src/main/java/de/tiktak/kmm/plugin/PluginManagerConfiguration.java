package de.tiktak.kmm.plugin;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.pf4j.PluginManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

import de.tiktak.kmm.settings.ApiKeyService;
import de.tiktak.kmm.settings.SettingsProxyAuthenticator;
import de.tiktak.kmm.settings.SettingsProxySelector;
import de.tiktak.kmm.plugin.api.TvShowScraper;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration(proxyBeanMethods = false)
class PluginManagerConfiguration {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    /** Shared plugin setting with the metadata language. */
    static final String LANGUAGE_SETTING = "language";

    /** Shared HTTP client for plugins and image downloads; the proxy comes from the settings. */
    @Bean
    HttpClient pluginHttpClient(SettingsService settingsService) {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(new SettingsProxySelector(settingsService))
                .authenticator(new SettingsProxyAuthenticator(settingsService))
                .build();
    }

    @Bean
    PluginManager pluginManager(PluginProperties properties, HttpClient pluginHttpClient,
            ObjectProvider<BuildProperties> buildProperties, SettingsService settingsService,
            ApiKeyService apiKeyService) throws IOException {
        Path directory = Files.createDirectories(properties.directory());
        log.info("Loading plugins from {}", directory.toAbsolutePath());
        return new KmmPluginManager(directory, new PluginContextFactory(properties, pluginHttpClient, buildProperties,
                () -> sharedSettings(settingsService.current()), apiKeyService::overlay));
    }

    /** Languages and episode ordering from the settings store; {@link ScrapeLanguage} takes precedence. */
    static Map<String, String> sharedSettings(KmmSettings settings) {
        String language = ScrapeLanguage.current();
        return Map.of(
                LANGUAGE_SETTING, language != null ? language : settings.metadataLanguage(),
                TvShowScraper.TV_LANGUAGE_SETTING, language != null ? language : settings.tv().metadataLanguage(),
                TvShowScraper.EPISODE_ORDERING_SETTING, settings.tv().episodeOrdering().name());
    }

    @Bean
    PluginLifecycle pluginLifecycle(PluginManager pluginManager, PluginProperties properties,
            ResourcePatternResolver resources) {
        return new PluginLifecycle(pluginManager, properties, new BundledPluginInstaller(resources));
    }
}
