package de.tiktak.kmm.plugin.tmdb;

import java.util.List;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.ScrapeRequest;

/**
 * Poster, Fanart und Logos von The Movie Database (TMDB).
 */
@Extension
public class TmdbImageScraper extends AbstractTmdbScraper implements ImageScraper {

    @Override
    public List<ImageCandidate> findImages(ScrapeRequest request) {
        TmdbApi api = api();
        if (!supports(request.mediaType())) {
            return List.of();
        }
        TmdbImageMapper mapper = new TmdbImageMapper(settings());
        return api.tmdbIdOf(request.uniqueIds())
                .flatMap(api::movieImages)
                .map(mapper::toCandidates)
                .orElse(List.of());
    }
}
