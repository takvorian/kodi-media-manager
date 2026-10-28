package de.tiktak.kmm.movie;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.TrailerScraper;
import de.tiktak.kmm.settings.SettingsService;
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
    private static final String ENGLISH = "en";
    private static final String TRAILER_TYPE = "Trailer";
    /** Kodi spielt Plugin-Links (z. B. YouTube-Addon) sowie Web- und Dateipfade ab. */
    private static final Pattern PLAYABLE = Pattern.compile("(?i)(plugin|https?|smb|nfs)://\\S+");

    private final MovieRepository movieRepository;
    private final MovieMetadataService metadataService;
    private final MovieNfoSync nfoSync;
    private final MovieSupplementWriter supplementWriter;
    private final ScraperRegistry scraperRegistry;
    private final SettingsService settingsService;

    /** Vorschläge, die besten zuerst: bevorzugte Sprache, dann Englisch; Trailer vor Teasern, offizielle zuerst. */
    public TrailerOptions candidates(long movieId) {
        MovieMetadata metadata = metadataService.findDetails(movieId).metadata();
        Map<String, String> ids = uniqueIds(metadata);
        if (ids.isEmpty()) {
            throw ScraperProblemException.missingIdsForTrailers();
        }
        List<String> warnings = new ArrayList<>();
        List<TrailerOption> options = new ArrayList<>();
        ScrapeRequest request = new ScrapeRequest(MediaType.MOVIE, metadata.title(), metadata.year(), ids);
        for (TrailerScraper scraper : scraperRegistry.trailerScrapersFor(MediaType.MOVIE)) {
            Optional<String> missing = scraper.missingConfiguration();
            if (missing.isPresent()) {
                warnings.add(missing.get());
                continue;
            }
            try {
                scraper.findTrailers(request).forEach(candidate ->
                        options.add(TrailerOption.of(candidate, scraper.displayName())));
            } catch (ScraperException e) {
                warnings.add("%s: %s".formatted(scraper.displayName(), e.getMessage()));
            }
        }
        options.sort(preference(settingsService.current().artworkLanguage()));
        return new TrailerOptions(options, warnings);
    }

    /**
     * Übernimmt den Link in Datenbank und NFO.
     *
     * @throws ScraperProblemException wenn der Link nichts ist, was Kodi abspielen kann
     */
    @Transactional
    public MovieDetails apply(long movieId, String url) {
        String trailer = url == null ? "" : url.strip();
        if (!PLAYABLE.matcher(trailer).matches()) {
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
        supplementWriter.write(movie, movie.supplement());
        log.info("Trailer of movie {} set to {}", movieId, trailer);
        return metadataService.findDetails(movieId);
    }

    private Movie require(long movieId) {
        return movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
    }

    static Comparator<TrailerOption> preference(String preferredLanguage) {
        return Comparator.comparingInt((TrailerOption option) -> languageRank(option.language(), preferredLanguage))
                .thenComparing(option -> !TRAILER_TYPE.equalsIgnoreCase(option.type()))
                .thenComparing(option -> !option.official())
                .thenComparing(option -> option.height() == null ? 0 : option.height(), Comparator.reverseOrder())
                .thenComparing(option -> option.published() == null ? "" : option.published(),
                        Comparator.reverseOrder());
    }

    private static int languageRank(String language, String preferred) {
        String normalized = language == null ? "" : language.toLowerCase(Locale.ROOT);
        if (normalized.equals(preferred)) {
            return 0;
        }
        return normalized.equals(ENGLISH) ? 1 : 2;
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

    /**
     * @param warnings Hinweise zu nicht eingerichteten oder fehlerhaften Anbietern
     */
    public record TrailerOptions(List<TrailerOption> candidates, List<String> warnings) {

        public TrailerOptions {
            candidates = List.copyOf(candidates);
            warnings = List.copyOf(warnings);
        }
    }
}
