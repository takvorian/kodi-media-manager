package de.tiktak.kmm.tvshow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.plugin.ScrapeLanguage;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.trailer.TrailerFinder;
import de.tiktak.kmm.trailer.TrailerOption;
import de.tiktak.kmm.trailer.TrailerOptions;
import lombok.RequiredArgsConstructor;

/**
 * Trailer of a TV show as a link in the {@code tvshow.nfo} ({@code <trailer>}) that Kodi plays: show proposals of
 * the trailer scrapers, apply or remove one, and add missing ones automatically. Nothing is downloaded.
 */
@Service
@RequiredArgsConstructor
public class TvShowTrailerService {

    private final TvShowRepository showRepository;
    private final TvShowMetadataService metadataService;
    private final TvNfoSync nfoSync;
    private final TrailerFinder trailerFinder;
    private final SettingsService settingsService;

    /** Proposals, best first: preferred language of the TV show images, then English. */
    @Transactional(readOnly = true)
    public TrailerOptions candidates(long showId) {
        TvShow show = requireShow(showId);
        Map<String, String> ids = uniqueIds(show.metadata());
        if (ids.isEmpty()) {
            throw TvShowProblemException.missingIdsForTrailers();
        }
        return ScrapeLanguage.with(metadataService.scrapeLanguage(showId), () -> trailerFinder.find(
                new ScrapeRequest(MediaType.TV_SHOW, show.getTitle(), show.getReleaseYear(), ids),
                ScrapeLanguage.preferredImageLanguage(settingsService.current().tv().artworkLanguage())));
    }

    /**
     * Applies the link to the database and NFO.
     *
     * @throws TvShowProblemException if the link is not something Kodi can play
     */
    public TvShowDetails apply(long showId, String url) {
        String trailer = url == null ? "" : url.strip();
        if (!TrailerFinder.isPlayable(trailer)) {
            throw TvShowProblemException.invalidTrailer(trailer);
        }
        return metadataService.changeTrailer(showId, trailer);
    }

    public TvShowDetails remove(long showId) {
        return metadataService.changeTrailer(showId, null);
    }

    /**
     * Enters the best proposal if the TV show has no trailer. An unknown trailer is read from the
     * NFO first; if it is unreadable, the TV show stays unchanged.
     *
     * @return whether a trailer was entered
     */
    @Transactional
    public boolean fillMissing(long showId) {
        TvShow show = requireShow(showId);
        nfoSync.refreshShowIfChangedOutside(show);
        if (!nfoSync.readTrailerIfUnknown(show) || show.getTrailer() != null
                || uniqueIds(show.metadata()).isEmpty()) {
            return false;
        }
        Optional<TrailerOption> best = candidates(showId).candidates().stream().findFirst();
        best.ifPresent(option -> metadataService.changeTrailer(showId, option.url()));
        return best.isPresent();
    }

    private TvShow requireShow(long showId) {
        return showRepository.findById(showId).orElseThrow(() -> TvShowProblemException.showNotFound(showId));
    }

    /** Order TVDB, TMDB, IMDb as in the NFO; each trailer scraper picks the matching one. */
    private static Map<String, String> uniqueIds(TvShowMetadata metadata) {
        Map<String, String> ids = new LinkedHashMap<>();
        putIfPresent(ids, TvNfoMapper.TVDB, metadata.tvdbId());
        putIfPresent(ids, TvNfoMapper.TMDB, metadata.tmdbId());
        putIfPresent(ids, TvNfoMapper.IMDB, metadata.imdbId());
        return ids;
    }

    private static void putIfPresent(Map<String, String> ids, String type, String value) {
        if (value != null) {
            ids.put(type, value);
        }
    }
}
