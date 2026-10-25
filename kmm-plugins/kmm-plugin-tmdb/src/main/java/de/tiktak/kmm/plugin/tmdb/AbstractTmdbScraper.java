package de.tiktak.kmm.plugin.tmdb;

import java.util.Optional;
import java.util.Set;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.ScraperException;

/**
 * Gemeinsame Grundlage der TMDB-Scraper: Kennung, Einrichtung und Prüfung des API-Keys.
 */
abstract class AbstractTmdbScraper implements Scraper {

    static final String ID = "tmdb";
    static final String DISPLAY_NAME = "The Movie Database (TMDB)";
    static final String MISSING_API_KEY = "Für TMDB fehlt ein API-Key (Umgebungsvariable KMM_TMDB_API_KEY).";

    private TmdbSettings settings;
    private TmdbApi api;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE);
    }

    @Override
    public void initialize(PluginContext context) {
        settings = TmdbSettings.from(context);
        api = new TmdbApi(new TmdbClient(context.httpClient(), settings, context.userAgent()), settings);
    }

    @Override
    public Optional<String> missingConfiguration() {
        return settings != null && settings.hasApiKey() ? Optional.empty() : Optional.of(MISSING_API_KEY);
    }

    /**
     * @throws ScraperException wenn der API-Key fehlt
     */
    protected TmdbApi api() {
        if (api == null) {
            throw new IllegalStateException("TMDB scraper has not been initialized");
        }
        missingConfiguration().ifPresent(message -> {
            throw new ScraperException(message);
        });
        return api;
    }

    protected TmdbSettings settings() {
        return settings;
    }
}
