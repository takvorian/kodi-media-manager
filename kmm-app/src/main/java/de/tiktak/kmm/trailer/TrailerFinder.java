package de.tiktak.kmm.trailer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.TrailerScraper;
import lombok.RequiredArgsConstructor;

/**
 * Searches trailers for movies and TV shows at the trailer scrapers and checks entered links. Trailers are only a
 * link in the NFO ({@code <trailer>}); nothing is downloaded.
 */
@Service
@RequiredArgsConstructor
public class TrailerFinder {

    private static final String ENGLISH = "en";
    private static final String TRAILER_TYPE = "Trailer";
    /** Kodi plays plugin links (e.g. the YouTube add-on) as well as web and file paths. */
    private static final Pattern PLAYABLE = Pattern.compile("(?i)(plugin|https?|smb|nfs)://\\S+");

    private final ScraperRegistry scraperRegistry;

    /**
     * Suggestions, the best first: preferred language, then English; trailers before teasers, official ones first.
     *
     * @param preferredLanguage preferred language (ISO 639-1), e.g. {@code de}
     */
    public TrailerOptions find(ScrapeRequest request, String preferredLanguage) {
        List<LocalizedText> warnings = new ArrayList<>();
        List<TrailerOption> options = new ArrayList<>();
        for (TrailerScraper scraper : scraperRegistry.trailerScrapersFor(request.mediaType())) {
            var missing = scraper.missingConfiguration();
            if (missing.isPresent()) {
                warnings.add(hint(missing.get()));
                continue;
            }
            try {
                scraper.findTrailers(request).forEach(candidate ->
                        options.add(TrailerOption.of(candidate, scraper.displayName())));
            } catch (ScraperException e) {
                warnings.add(LocalizedText.of("trailer.warning")
                        .with("provider", scraper.displayName())
                        .with("reason", e.localizedText()));
            }
        }
        options.sort(preference(preferredLanguage));
        return new TrailerOptions(options, warnings);
    }

    /** Plugins word the hint as a translatable text or as plain text. */
    private static LocalizedText hint(Object hint) {
        return hint instanceof LocalizedText text ? text : LocalizedText.literal(String.valueOf(hint));
    }

    /** Whether Kodi can play the link: {@code plugin://}, {@code http(s)://}, {@code smb://} or {@code nfs://}. */
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
