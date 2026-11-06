package de.tiktak.kmm.settings;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * API keys of the bundled scraper plugins. They live in the settings store; the configuration (environment
 * variables) only fills in a key that has not been stored yet.
 *
 * @see ApiKeyService
 */
public enum ApiKey {
    TMDB_API_KEY("tmdb", "api-key", "tmdbApiKey"),
    TVDB_API_KEY("tvdb", "api-key", "tvdbApiKey"),
    TVDB_PIN("tvdb", "pin", "tvdbPin"),
    FANARTTV_API_KEY("fanarttv", "api-key", "fanartTvApiKey"),
    FANARTTV_CLIENT_KEY("fanarttv", "client-key", "fanartTvClientKey"),
    TRAKT_CLIENT_ID("trakt", "client-id", "traktClientId"),
    OMDB_API_KEY("omdb", "api-key", "omdbApiKey");

    private static final String STORE_PREFIX = "plugin.";
    private static final String LABEL_KEY_PREFIX = "settings.apiKey.";

    private final String pluginId;
    private final String setting;
    private final LocalizedText label;

    ApiKey(String pluginId, String setting, String labelKey) {
        this.pluginId = pluginId;
        this.setting = setting;
        this.label = LocalizedText.of(LABEL_KEY_PREFIX + labelKey);
    }

    /** ID of the plugin that receives the key as a setting. */
    public String pluginId() {
        return pluginId;
    }

    /** Name of the setting in the plugin, e.g. {@code api-key}. */
    public String setting() {
        return setting;
    }

    public LocalizedText label() {
        return label;
    }

    String storeKey() {
        return STORE_PREFIX + pluginId + "." + setting;
    }
}
