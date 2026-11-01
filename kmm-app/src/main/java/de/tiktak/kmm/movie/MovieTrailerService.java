package de.tiktak.kmm.movie;

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
import lombok.extern.slf4j.Slf4j;

/**
 * Trailer eines Films als Link in der NFO ({@code <trailer>}), den Kodi abspielt: Vorschläge der Trailer-Scraper
 * anzeigen, einen übernehmen oder entfernen und fehlende automatisch ergänzen. Heruntergeladen wird nichts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieTrailerService {

    private static final String TMDB = "tmdb";
    private static final String IMDB = "imdb";

    private final MovieRepository movieRepository;
    private final MovieMetadataService metadataService;
    private final MovieNfoSync nfoSync;
    private final MovieSupplementWriter supplementWriter;
    private final TrailerFinder trailerFinder;
    private final SettingsService settingsService;

    /** Vorschläge, die besten zuerst: bevorzugte Sprache der Bilder, dann Englisch. */
    public TrailerOptions candidates(long movieId) {
        MovieMetadata metadata = metadataService.findDetails(movieId).metadata();
        Map<String, String> ids = uniqueIds(metadata);
        if (ids.isEmpty()) {
            throw ScraperProblemException.missingIdsForTrailers();
        }
        return ScrapeLanguage.with(metadataService.scrapeLanguage(movieId), () -> trailerFinder.find(
                new ScrapeRequest(MediaType.MOVIE, metadata.title(), metadata.year(), ids),
                ScrapeLanguage.preferredImageLanguage(settingsService.current().artworkLanguage())));
    }

    /**
     * Übernimmt den Link in Datenbank und NFO.
     *
     * @throws ScraperProblemException wenn der Link nichts ist, was Kodi abspielen kann
     */
    @Transactional
    public MovieDetails apply(long movieId, String url) {
        String trailer = url == null ? "" : url.strip();
        if (!TrailerFinder.isPlayable(trailer)) {
            throw ScraperProblemException.invalidTrailer(trailer);
        }
        return change(movieId, trailer);
    }

    @Transactional
    public MovieDetails remove(long movieId) {
        return change(movieId, null);
    }

    /**
     * Trägt den besten Vorschlag ein, wenn der Film keinen Trailer hat.
     *
     * @return ob ein Trailer eingetragen wurde
     */
    @Transactional
    public boolean fillMissing(long movieId) {
        Movie movie = require(movieId);
        nfoSync.refreshIfChangedOutside(movie);
        if (!nfoSync.readTrailerIfUnknown(movie) || movie.getTrailer() != null
                || uniqueIds(movie.metadata()).isEmpty()) {
            return false;
        }
        Optional<TrailerOption> best = candidates(movieId).candidates().stream().findFirst();
        best.ifPresent(option -> change(movieId, option.url()));
        return best.isPresent();
    }

    private MovieDetails change(long movieId, String trailer) {
        Movie movie = require(movieId);
        nfoSync.refreshIfChangedOutside(movie);
        movie.changeTrailer(trailer);
        supplementWriter.writeTrailer(movie);
        log.info("Trailer of movie {} set to {}", movieId, trailer);
        return metadataService.findDetails(movieId);
    }

    private Movie require(long movieId) {
        return movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
    }

    private static Map<String, String> uniqueIds(MovieMetadata metadata) {
        Map<String, String> ids = new LinkedHashMap<>();
        if (metadata.tmdbId() != null) {
            ids.put(TMDB, metadata.tmdbId());
        }
        if (metadata.imdbId() != null) {
            ids.put(IMDB, metadata.imdbId());
        }
        return ids;
    }
}
