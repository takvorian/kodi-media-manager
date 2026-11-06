package de.tiktak.kmm.plugin.tvdb;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.Attribution;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ScraperTexts;

/**
 * Common base of the TVDB scrapers: identifier, setup, check of the API key and the attribution of the data source
 * that TVDB requires.
 */
abstract class AbstractTvdbScraper implements Scraper {

    static final String ID = "tvdb";
    static final String DISPLAY_NAME = "TheTVDB";
    /** Name of the provider in messages for the user. */
    static final String PROVIDER = "TVDB";
    static final LocalizedText MISSING_API_KEY = ScraperTexts.apiKeyMissing(PROVIDER);
    static final Attribution ATTRIBUTION = new Attribution(LocalizedText.of("scraper.tvdb.attribution"),
            URI.create("https://www.thetvdb.com"));

    private final Clock clock;
    private TvdbSettings settings;
    private TvdbApi api;

    protected AbstractTvdbScraper(Clock clock) {
        this.clock = clock;
    }

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
        return Set.of(MediaType.TV_SHOW);
    }

    @Override
    public void initialize(PluginContext context) {
        settings = TvdbSettings.from(context);
        TvdbClient client = new TvdbClient(context.httpClient(), settings, context.userAgent(), clock);
        api = new TvdbApi(client, settings, clock);
    }

    @Override
    public Optional<LocalizedText> missingConfiguration() {
        return settings != null && settings.hasApiKey() ? Optional.empty() : Optional.of(MISSING_API_KEY);
    }

    @Override
    public Optional<Attribution> attribution() {
        return Optional.of(ATTRIBUTION);
    }

    /**
     * @throws ScraperException if the API key is missing
     */
    protected TvdbApi api() {
        if (api == null) {
            throw new IllegalStateException("TVDB scraper has not been initialized");
        }
        missingConfiguration().ifPresent(message -> {
            throw new ScraperException(message);
        });
        return api;
    }

    protected TvdbSettings settings() {
        return settings;
    }
}
