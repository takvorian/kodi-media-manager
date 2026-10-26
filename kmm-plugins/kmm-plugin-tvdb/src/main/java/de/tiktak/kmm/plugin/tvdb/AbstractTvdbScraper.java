package de.tiktak.kmm.plugin.tvdb;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;
import java.util.Set;

import de.tiktak.kmm.plugin.api.Attribution;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.ScraperException;

/**
 * Gemeinsame Grundlage der TVDB-Scraper: Kennung, Einrichtung, Prüfung des API-Keys und der Hinweis
 * auf die Datenquelle, den TVDB verlangt.
 */
abstract class AbstractTvdbScraper implements Scraper {

    static final String ID = "tvdb";
    static final String DISPLAY_NAME = "TheTVDB";
    static final String MISSING_API_KEY = "Für TVDB fehlt ein API-Key (Einstellungen → API-Keys).";
    static final Attribution ATTRIBUTION = new Attribution(
            "Metadata provided by TheTVDB. Please consider adding missing information or subscribing.",
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
    public Optional<String> missingConfiguration() {
        return settings != null && settings.hasApiKey() ? Optional.empty() : Optional.of(MISSING_API_KEY);
    }

    @Override
    public Optional<Attribution> attribution() {
        return Optional.of(ATTRIBUTION);
    }

    /**
     * @throws ScraperException wenn der API-Key fehlt
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
