package de.tiktak.kmm.plugin.fanarttv;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ScraperTexts;

/**
 * Images from fanart.tv: poster, fanart, clearlogo, clearart, disc art, banner and landscape for movies, for TV shows
 * also character art and season images.
 */
@Extension
public class FanartTvImageScraper implements ImageScraper {

    static final String ID = "fanarttv";
    static final String DISPLAY_NAME = "fanart.tv";
    /** Name of the provider in messages for the user. */
    static final String PROVIDER = "fanart.tv";
    static final LocalizedText MISSING_API_KEY = ScraperTexts.apiKeyMissing(PROVIDER);
    private static final String TMDB = "tmdb";
    private static final String IMDB = "imdb";
    private static final String TVDB = "tvdb";

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
        return Set.of(MediaType.MOVIE, MediaType.MOVIE_SET, MediaType.TV_SHOW);
    }

    @Override
    public void initialize(PluginContext context) {
        settings = FanartTvSettings.from(context);
        client = new FanartTvClient(context.httpClient(), settings, context.userAgent());
    }

    @Override
    public Optional<LocalizedText> missingConfiguration() {
        return settings != null && settings.hasApiKey() ? Optional.empty() : Optional.of(MISSING_API_KEY);
    }

    /**
     * fanart.tv finds movies via the TMDB ID, alternatively via the IMDb ID, movie sets via the TMDB ID of the
     * collection (same endpoint) and TV shows via the TVDB ID.
     */
    @Override
    public List<ImageCandidate> findImages(ScrapeRequest request) {
        requireConfigured();
        return switch (request.mediaType()) {
            case MOVIE -> Optional.ofNullable(request.uniqueIds().get(TMDB))
                    .or(() -> Optional.ofNullable(request.uniqueIds().get(IMDB)))
                    .flatMap(client::movie)
                    .map(mapper::toCandidates)
                    .orElse(List.of());
            case MOVIE_SET -> Optional.ofNullable(request.uniqueIds().get(TMDB))
                    .flatMap(client::movie)
                    .map(mapper::toCandidates)
                    .orElse(List.of());
            case TV_SHOW -> Optional.ofNullable(request.uniqueIds().get(TVDB))
                    .flatMap(client::tvShow)
                    .map(mapper::toTvCandidates)
                    .orElse(List.of());
            default -> List.of();
        };
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
