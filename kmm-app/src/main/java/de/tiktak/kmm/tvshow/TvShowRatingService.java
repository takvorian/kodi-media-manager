package de.tiktak.kmm.tvshow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.rating.RatingFinder;
import lombok.RequiredArgsConstructor;

/**
 * Ergänzt fehlende Bewertungen weiterer Anbieter (etwa Trakt) in Bibliothek und {@code tvshow.nfo}. Vorhandene
 * Bewertungen bleiben.
 */
@Service
@RequiredArgsConstructor
public class TvShowRatingService {

    private final TvShowMetadataService metadataService;
    private final RatingFinder ratingFinder;

    /**
     * @return ob eine Bewertung ergänzt wurde
     * @throws TvShowProblemException wenn es die Serie nicht gibt oder die NFO nicht geschrieben werden kann
     */
    public boolean fillMissing(long showId) {
        return metadataService.changeExtras(showId, (metadata, extras) -> {
            Set<String> present = extras.ratings().stream().map(TvRating::source).collect(Collectors.toSet());
            List<ProviderRating> found = ratingFinder.findMissing(MediaType.TV_SHOW, metadata.title(),
                    metadata.year(), uniqueIds(metadata), present);
            if (found.isEmpty()) {
                return extras;
            }
            List<TvRating> ratings = new ArrayList<>(extras.ratings());
            found.forEach(rating -> ratings.add(new TvRating(rating.source(), rating.value(), rating.max(),
                    rating.votes(), extras.ratings().isEmpty() && ratings.isEmpty())));
            return extras.withRatings(ratings);
        });
    }

    private static Map<String, String> uniqueIds(TvShowMetadata metadata) {
        Map<String, String> ids = new LinkedHashMap<>();
        putIfPresent(ids, TvNfoMapper.IMDB, metadata.imdbId());
        putIfPresent(ids, TvNfoMapper.TVDB, metadata.tvdbId());
        putIfPresent(ids, TvNfoMapper.TMDB, metadata.tmdbId());
        return ids;
    }

    private static void putIfPresent(Map<String, String> ids, String type, String value) {
        if (value != null) {
            ids.put(type, value);
        }
    }
}
