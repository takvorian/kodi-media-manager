package de.tiktak.kmm.tvshow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.trailer.TrailerFinder;
import de.tiktak.kmm.trailer.TrailerOption;
import de.tiktak.kmm.trailer.TrailerOptions;
import lombok.RequiredArgsConstructor;

/**
 * Trailer einer Serie als Link in der {@code tvshow.nfo} ({@code <trailer>}), den Kodi abspielt: Vorschläge der
 * Trailer-Scraper anzeigen, einen übernehmen oder entfernen und fehlende automatisch ergänzen. Heruntergeladen wird
 * nichts.
 */
@Service
@RequiredArgsConstructor
public class TvShowTrailerService {

    private final TvShowRepository showRepository;
    private final TvShowMetadataService metadataService;
    private final TvNfoSync nfoSync;
    private final TrailerFinder trailerFinder;
    private final SettingsService settingsService;

    /** Vorschläge, die besten zuerst: bevorzugte Sprache der Serienbilder, dann Englisch. */
    @Transactional(readOnly = true)
    public TrailerOptions candidates(long showId) {
        TvShow show = requireShow(showId);
        Map<String, String> ids = uniqueIds(show.metadata());
        if (ids.isEmpty()) {
            throw TvShowProblemException.missingIdsForTrailers();
        }
        return trailerFinder.find(new ScrapeRequest(MediaType.TV_SHOW, show.getTitle(), show.getReleaseYear(), ids),
                settingsService.current().tv().artworkLanguage());
    }

    /**
     * Übernimmt den Link in Datenbank und NFO.
     *
     * @throws TvShowProblemException wenn der Link nichts ist, was Kodi abspielen kann
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
     * Trägt den besten Vorschlag ein, wenn die Serie keinen Trailer hat. Ein unbekannter Trailer wird vorher aus der
     * NFO gelesen; ist sie nicht lesbar, bleibt die Serie unverändert.
     *
     * @return ob ein Trailer eingetragen wurde
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

    /** Reihenfolge TVDB, TMDB, IMDb wie in der NFO; jeder Trailer-Scraper sucht sich die passende aus. */
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
