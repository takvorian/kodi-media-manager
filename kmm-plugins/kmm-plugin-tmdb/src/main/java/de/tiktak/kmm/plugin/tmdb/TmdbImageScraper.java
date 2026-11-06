package de.tiktak.kmm.plugin.tmdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.ImageCandidate;
import de.tiktak.kmm.plugin.api.ImageScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;

/**
 * Posters, fanart and logos from The Movie Database (TMDB) for movies, movie sets (collections) and TV shows, for TV
 * shows also the season posters.
 */
@Extension
public class TmdbImageScraper extends AbstractTmdbScraper implements ImageScraper {

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE, MediaType.MOVIE_SET, MediaType.TV_SHOW);
    }

    @Override
    public List<ImageCandidate> findImages(ScrapeRequest request) {
        TmdbApi api = api();
        TmdbImageMapper mapper = new TmdbImageMapper(settings());
        return switch (request.mediaType()) {
            case MOVIE -> api.tmdbIdOf(request.uniqueIds())
                    .flatMap(api::movieImages)
                    .map(mapper::toCandidates)
                    .orElse(List.of());
            case MOVIE_SET -> Optional.ofNullable(request.uniqueIds().get(TmdbApi.TMDB))
                    .flatMap(api::collectionImages)
                    .map(mapper::toCandidates)
                    .orElse(List.of());
            case TV_SHOW -> api.tvShowIdOf(request.uniqueIds())
                    .map(tmdbId -> tvShowImages(api, mapper, tmdbId))
                    .orElse(List.of());
            default -> List.of();
        };
    }

    private static List<ImageCandidate> tvShowImages(TmdbApi api, TmdbImageMapper mapper, String tmdbId) {
        List<ImageCandidate> candidates = new ArrayList<>();
        api.tvImages(tmdbId).map(mapper::toCandidates).ifPresent(candidates::addAll);
        api.tvShow(tmdbId).ifPresent(show -> JsonNodes.elements(show.path("seasons"))
                .map(season -> season.path("season_number").asInt(-1))
                .filter(number -> number >= 0)
                .forEach(number -> api.tvSeasonImages(tmdbId, number)
                        .map(images -> mapper.toSeasonCandidates(images, number))
                        .ifPresent(candidates::addAll)));
        return candidates;
    }
}
