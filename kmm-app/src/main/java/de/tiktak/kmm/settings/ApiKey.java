package de.tiktak.kmm.settings;

/**
 * API-Keys der mitgelieferten Scraper-Plugins. Sie stehen im Einstellungsspeicher; die Konfiguration
 * (Umgebungsvariablen) füllt nur einen noch nicht gespeicherten Key.
 *
 * @see ApiKeyService
 */
public enum ApiKey {
    TMDB_API_KEY("tmdb", "api-key", "TMDB-API-Key"),
    TVDB_API_KEY("tvdb", "api-key", "TVDB-API-Key (v4)"),
    TVDB_PIN("tvdb", "pin", "TVDB-Abonnenten-PIN"),
    FANARTTV_API_KEY("fanarttv", "api-key", "fanart.tv-Projekt-Key"),
    FANARTTV_CLIENT_KEY("fanarttv", "client-key", "fanart.tv-Persönlicher-Key"),
    TRAKT_CLIENT_ID("trakt", "client-id", "Trakt-Client-ID");

    private static final String STORE_PREFIX = "plugin.";

    private final String pluginId;
    private final String setting;
    private final String label;

    ApiKey(String pluginId, String setting, String label) {
        this.pluginId = pluginId;
        this.setting = setting;
        this.label = label;
    }

    /** ID des Plugins, das den Key als Einstellung erhält. */
    public String pluginId() {
        return pluginId;
    }

    /** Name der Einstellung im Plugin, z. B. {@code api-key}. */
    public String setting() {
        return setting;
    }

    public String label() {
        return label;
    }

    String storeKey() {
        return STORE_PREFIX + pluginId + "." + setting;
    }
}
