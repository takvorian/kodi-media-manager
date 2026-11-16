package de.tiktak.kmm.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.plugin.api.EnrichmentScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.Scraper;
import lombok.RequiredArgsConstructor;

/**
 * Order of the scrapers per kind: chosen in the settings for movies and TV shows, and per source differently if
 * needed. Scrapers that are switched off there are not asked; scrapers installed later come last.
 */
@Service
@RequiredArgsConstructor
public class ScraperPriorityService {

    private final ScraperRegistry scraperRegistry;
    private final ScraperPriorityStore store;
    private final ScraperDefaults defaults;

    /**
     * Sorts the scrapers by the order that applies to the source and leaves out those switched off there.
     *
     * @param mediaType what is scraped; seasons and episodes count as TV shows, movie sets as movies
     * @param sourceId  the source of the movie or TV show, {@code null} for the order from the settings
     */
    public <T extends Scraper> List<T> ordered(ScraperKind kind, MediaType mediaType, Long sourceId,
            List<T> scrapers) {
        MediaType group = groupOf(mediaType);
        List<ScraperOrder.Entry> order = effective(kind, group, sourceId, scrapers);
        Map<String, T> byId = new LinkedHashMap<>();
        scrapers.forEach(scraper -> byId.putIfAbsent(scraper.id(), scraper));
        return order.stream()
                .filter(ScraperOrder.Entry::enabled)
                .map(entry -> byId.get(entry.scraperId()))
                .toList();
    }

    /** The orders from the settings for movies and TV shows. */
    @Transactional(readOnly = true)
    public List<ScraperPriorityView> global() {
        List<ScraperPriorityView> views = new ArrayList<>();
        for (MediaType group : List.of(MediaType.MOVIE, MediaType.TV_SHOW)) {
            kindsOf(group).forEach(kind -> views.add(view(kind, group, null, false)));
        }
        return views;
    }

    /** The orders of a source; a kind without its own order shows the one from the settings. */
    @Transactional(readOnly = true)
    public List<ScraperPriorityView> ofSource(long sourceId, MediaType mediaType) {
        Map<ScraperKind, ScraperOrder> own = store.ofSource(sourceId);
        return kindsOf(mediaType).stream()
                .map(kind -> view(kind, mediaType, sourceId, !own.containsKey(kind)))
                .toList();
    }

    /** Stores orders in the settings; kinds not mentioned remain. */
    @Transactional
    public List<ScraperPriorityView> changeGlobal(List<ScraperPriorityChange> changes) {
        changes.forEach(change -> store.saveGlobal(change.mediaType(), change.kind(), change.order()));
        return global();
    }

    /** Stores the orders of a source; {@code inherit} returns a kind to the order from the settings. */
    @Transactional
    public List<ScraperPriorityView> changeForSource(long sourceId, MediaType mediaType,
            List<ScraperPriorityChange> changes) {
        changes.stream()
                .filter(change -> change.mediaType() == mediaType)
                .forEach(change -> store.saveForSource(sourceId, change.kind(),
                        change.inherit() ? Optional.empty() : Optional.of(change.order())));
        return ofSource(sourceId, mediaType);
    }

    private ScraperPriorityView view(ScraperKind kind, MediaType group, Long sourceId, boolean inherited) {
        List<? extends Scraper> scrapers = available(kind, group);
        Map<String, Scraper> byId = new LinkedHashMap<>();
        scrapers.forEach(scraper -> byId.putIfAbsent(scraper.id(), scraper));
        List<ScraperPriorityView.Entry> entries = effective(kind, group, sourceId, scrapers).stream()
                .map(entry -> ScraperPriorityView.Entry.of(byId.get(entry.scraperId()), entry.enabled()))
                .toList();
        return new ScraperPriorityView(kind, group, inherited, entries);
    }

    /** The stored order restricted to the given scrapers, the missing ones appended in their default order. */
    private List<ScraperOrder.Entry> effective(ScraperKind kind, MediaType group, Long sourceId,
            List<? extends Scraper> scrapers) {
        Optional<ScraperOrder> stored = Optional.ofNullable(sourceId)
                .map(id -> store.ofSource(id).get(kind))
                .or(() -> store.global(group, kind));
        List<String> ids = scrapers.stream().map(Scraper::id).distinct().toList();
        List<ScraperOrder.Entry> result = new ArrayList<>();
        stored.ifPresent(order -> order.entries().stream()
                .filter(entry -> ids.contains(entry.scraperId()))
                .forEach(result::add));
        defaultOrder(kind, group, scrapers).stream()
                .filter(id -> result.stream().noneMatch(entry -> entry.scraperId().equals(id)))
                .forEach(id -> result.add(new ScraperOrder.Entry(id, true)));
        return result;
    }

    /**
     * Ratings by their own order, everything else with the preferred metadata scraper of movies or TV shows first
     * (its artwork and trailers fit its data best), then by name.
     */
    private List<String> defaultOrder(ScraperKind kind, MediaType group, List<? extends Scraper> scrapers) {
        Comparator<Scraper> byName = Comparator.comparing(Scraper::displayName,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        String preferred = group == MediaType.TV_SHOW ? defaults.preferredTvShowScraper()
                : defaults.preferredMetadataScraper();
        Comparator<Scraper> comparator = kind == ScraperKind.ENRICHMENT
                ? Comparator.comparingInt((Scraper scraper) -> scraper instanceof EnrichmentScraper enrichment
                        ? enrichment.order() : Integer.MAX_VALUE).thenComparing(byName)
                : Comparator.comparing((Scraper scraper) -> !preferred.equals(scraper.id())).thenComparing(byName);
        return scrapers.stream().sorted(comparator).map(Scraper::id).distinct().toList();
    }

    private List<? extends Scraper> available(ScraperKind kind, MediaType group) {
        return switch (kind) {
            case METADATA -> group == MediaType.TV_SHOW ? scraperRegistry.tvShowScrapers()
                    : scraperRegistry.metadataScrapersFor(group);
            case ARTWORK -> scraperRegistry.imageScrapersFor(group);
            case ENRICHMENT -> scraperRegistry.enrichmentScrapersFor(group);
            case THEME -> scraperRegistry.themeScrapersFor(group);
            case TRAILER -> scraperRegistry.trailerScrapersFor(group);
            case MOVIE_SET -> scraperRegistry.movieSetScrapers();
        };
    }

    /** The kinds that can be ordered for movies or TV shows; movie sets only belong to movies. */
    static List<ScraperKind> kindsOf(MediaType group) {
        return Arrays.stream(ScraperKind.values())
                .filter(kind -> kind != ScraperKind.MOVIE_SET || group == MediaType.MOVIE)
                .toList();
    }

    static MediaType groupOf(MediaType mediaType) {
        return switch (mediaType) {
            case MOVIE, MOVIE_SET -> MediaType.MOVIE;
            case TV_SHOW, TV_SEASON, TV_EPISODE -> MediaType.TV_SHOW;
        };
    }
}
