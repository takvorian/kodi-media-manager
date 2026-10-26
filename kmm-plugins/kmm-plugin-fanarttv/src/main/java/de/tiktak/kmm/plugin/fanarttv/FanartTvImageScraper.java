package de.tiktak.kmm.plugin.fanarttv;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;

/**
 * Bilder von fanart.tv: Poster, Fanart, Clearlogo, Clearart, Disc-Art, Banner und Landscape.
 */
@Extension
public class FanartTvImageScraper implements ImageScraper {

    static final String ID = "fanarttv";
    static final String DISPLAY_NAME = "fanart.tv";
    static final String MISSING_API_KEY = "Für fanart.tv fehlt ein API-Key (Einstellungen → API-Keys).";
    private static final String TMDB = "tmdb";
    private static final String IMDB = "imdb";

    private final FanartTvImageMapper mapper = new FanartTvImageMapper();
    private FanartTvSettings settings;
    private FanartTvClient client;

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
        settings = FanartTvSettings.from(context);
        client = new FanartTvClient(context.httpClient(), settings, context.userAgent());
    }

    @Override
    public Optional<String> missingConfiguration() {
        return settings != null && settings.hasApiKey() ? Optional.empty() : Optional.of(MISSING_API_KEY);
    }

    /** fanart.tv findet Filme über die TMDB-ID, ersatzweise über die IMDb-ID. */
    @Override
    public List<ImageCandidate> findImages(ScrapeRequest request) {
        requireConfigured();
        if (!supports(request.mediaType())) {
            return List.of();
        }
        return Optional.ofNullable(request.uniqueIds().get(TMDB))
                .or(() -> Optional.ofNullable(request.uniqueIds().get(IMDB)))
                .flatMap(client::movie)
                .map(mapper::toCandidates)
                .orElse(List.of());
    }

    private void requireConfigured() {
        if (client == null) {
            throw new IllegalStateException("fanart.tv scraper has not been initialized");
        }
        missingConfiguration().ifPresent(message -> {
            throw new ScraperException(message);
        });
    }
}
