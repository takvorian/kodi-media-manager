package de.tiktak.kmm.trailer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.TrailerScraper;
import lombok.RequiredArgsConstructor;

/**
 * Sucht Trailer für Filme und Serien bei den Trailer-Scrapern und prüft eingetragene Links. Trailer stehen nur als
 * Link in der NFO ({@code <trailer>}); heruntergeladen wird nichts.
 */
@Service
@RequiredArgsConstructor
public class TrailerFinder {

    private static final String ENGLISH = "en";
    private static final String TRAILER_TYPE = "Trailer";
    /** Kodi spielt Plugin-Links (z. B. YouTube-Addon) sowie Web- und Dateipfade ab. */
    private static final Pattern PLAYABLE = Pattern.compile("(?i)(plugin|https?|smb|nfs)://\\S+");

    private final ScraperRegistry scraperRegistry;

    /**
     * Vorschläge, die besten zuerst: bevorzugte Sprache, dann Englisch; Trailer vor Teasern, offizielle zuerst.
     *
     * @param preferredLanguage bevorzugte Sprache (ISO 639-1), z. B. {@code de}
     */
    public TrailerOptions find(ScrapeRequest request, String preferredLanguage) {
        List<String> warnings = new ArrayList<>();
        List<TrailerOption> options = new ArrayList<>();
        for (TrailerScraper scraper : scraperRegistry.trailerScrapersFor(request.mediaType())) {
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
        options.sort(preference(preferredLanguage));
        return new TrailerOptions(options, warnings);
    }

    /** Ob Kodi den Link abspielen kann: {@code plugin://}, {@code http(s)://}, {@code smb://} oder {@code nfs://}. */
    public static boolean isPlayable(String url) {
        return url != null && PLAYABLE.matcher(url).matches();
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
}
