package de.tiktak.kmm.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Liest und schreibt {@link KmmSettings} als Schlüssel-Wert-Paare. Nicht gespeicherte Werte
 * kommen aus {@link KmmSettings#DEFAULTS}; Listen stehen zeilenweise in einem Wert.
 */
@Component
@RequiredArgsConstructor
class SettingsStore {

    static final String METADATA_LANGUAGE = "metadata-language";
    static final String ARTWORK_LANGUAGE = "artwork-language";
    static final String VIDEO_EXTENSIONS = "video-extensions";
    static final String EXCLUDED_PATHS = "excluded-paths";
    static final String PROXY_HOST = "proxy-host";
    static final String PROXY_PORT = "proxy-port";
    static final String PROXY_USERNAME = "proxy-username";
    static final String PROXY_PASSWORD = "proxy-password";
    static final String TV_METADATA_LANGUAGE = "tv-metadata-language";
    static final String TV_ARTWORK_LANGUAGE = "tv-artwork-language";
    static final String TV_EPISODE_ORDERING = "tv-episode-ordering";

    private static final String LINE_SEPARATOR = "\n";

    private final AppSettingRepository repository;

    KmmSettings load() {
        Map<String, String> values = repository.findAll().stream()
                .collect(Collectors.toMap(AppSetting::getKey, AppSetting::getValue));
        KmmSettings defaults = KmmSettings.DEFAULTS;
        String metadataLanguage = values.getOrDefault(METADATA_LANGUAGE, defaults.metadataLanguage());
        String artworkLanguage = values.getOrDefault(ARTWORK_LANGUAGE, defaults.artworkLanguage());
        return new KmmSettings(
                metadataLanguage,
                artworkLanguage,
                values.containsKey(VIDEO_EXTENSIONS) ? lines(values.get(VIDEO_EXTENSIONS)) : defaults.videoExtensions(),
                values.containsKey(EXCLUDED_PATHS) ? lines(values.get(EXCLUDED_PATHS)) : defaults.excludedPaths(),
                values.containsKey(PROXY_HOST) ? proxy(values) : null,
                new TvSettings(
                        values.getOrDefault(TV_METADATA_LANGUAGE, metadataLanguage),
                        values.getOrDefault(TV_ARTWORK_LANGUAGE, artworkLanguage),
                        EpisodeOrdering.valueOf(values.getOrDefault(TV_EPISODE_ORDERING, EpisodeOrdering.AIRED.name()))));
    }

    void save(KmmSettings settings) {
        put(METADATA_LANGUAGE, settings.metadataLanguage());
        put(ARTWORK_LANGUAGE, settings.artworkLanguage());
        put(VIDEO_EXTENSIONS, String.join(LINE_SEPARATOR, settings.videoExtensions()));
        put(EXCLUDED_PATHS, String.join(LINE_SEPARATOR, settings.excludedPaths()));
        ProxySettings proxy = settings.proxy();
        put(PROXY_HOST, proxy == null ? null : proxy.host());
        put(PROXY_PORT, proxy == null ? null : String.valueOf(proxy.port()));
        put(PROXY_USERNAME, proxy == null ? null : proxy.username());
        put(PROXY_PASSWORD, proxy == null ? null : proxy.password());
        put(TV_METADATA_LANGUAGE, settings.tv().metadataLanguage());
        put(TV_ARTWORK_LANGUAGE, settings.tv().artworkLanguage());
        put(TV_EPISODE_ORDERING, settings.tv().episodeOrdering().name());
    }

    private void put(String key, String value) {
        if (value == null) {
            repository.deleteById(key);
            return;
        }
        repository.findById(key).ifPresentOrElse(
                setting -> setting.changeValue(value),
                () -> repository.save(new AppSetting(key, value)));
    }

    private static ProxySettings proxy(Map<String, String> values) {
        return new ProxySettings(values.get(PROXY_HOST), Integer.parseInt(values.get(PROXY_PORT)),
                values.get(PROXY_USERNAME), values.get(PROXY_PASSWORD));
    }

    private static List<String> lines(String value) {
        return value.isEmpty() ? List.of() : Arrays.asList(value.split(LINE_SEPARATOR));
    }
}
