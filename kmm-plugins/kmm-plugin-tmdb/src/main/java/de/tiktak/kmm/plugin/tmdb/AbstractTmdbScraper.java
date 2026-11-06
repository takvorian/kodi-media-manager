package de.tiktak.kmm.plugin.tmdb;

import java.util.Optional;
import java.util.Set;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ScraperTexts;

/**
 * Common base of the TMDB scrapers: identifier, setup and check of the API key.
 */
abstract class AbstractTmdbScraper implements Scraper {

    static final String ID = "tmdb";
    static final String DISPLAY_NAME = "The Movie Database (TMDB)";
    /** Name of the provider in messages for the user. */
    static final String PROVIDER = "TMDB";
    static final LocalizedText MISSING_API_KEY = ScraperTexts.apiKeyMissing(PROVIDER);

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
    public Optional<LocalizedText> missingConfiguration() {
        return settings != null && settings.hasApiKey() ? Optional.empty() : Optional.of(MISSING_API_KEY);
    }

    /**
     * @throws ScraperException if the API key is missing
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
