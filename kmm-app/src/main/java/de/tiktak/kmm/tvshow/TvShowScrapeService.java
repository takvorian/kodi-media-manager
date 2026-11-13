package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.plugin.ScrapeLanguage;
import de.tiktak.kmm.plugin.ScraperRegistry;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.SearchQuery;
import de.tiktak.kmm.plugin.api.TvShowResult;
import de.tiktak.kmm.plugin.api.TvShowScraper;
import de.tiktak.kmm.scanner.TvShowFiles;
import de.tiktak.kmm.tvshow.TvShowScrapeMerger.EpisodeFill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Online search for TV shows: searches the TV show scraper, proposes metadata for the form, fills
 * empty details of the episodes and determines missing episodes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowScrapeService {

    private final ScraperRegistry scraperRegistry;
    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final TvShowNfoFiles nfoFiles;
    private final TvNfoSync nfoSync;
    private final TvShowResultCache cache;
    private final TvShowMetadataService metadataService;
    private final TvScrapingProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * @param title search term; if omitted, the title of the TV show
     * @param year  year; if omitted, the year of the TV show, provided no title was given
     */
    @Transactional(readOnly = true)
    public List<TvShowScrapeCandidate> search(long showId, String scraperId, String title, Integer year) {
        TvShow show = requireShow(showId);
        boolean customTitle = title != null && !title.isBlank();
        SearchQuery query = new SearchQuery(MediaType.TV_SHOW, customTitle ? title : show.getTitle(),
                customTitle ? year : show.getReleaseYear());
        return inLanguageOf(showId, () -> {
            TvShowScraper scraper = scraper(scraperId);
            return call(scraper, s -> s.search(query)).stream().map(TvShowScrapeCandidate::from).toList();
        });
    }

    @Transactional(readOnly = true)
    public TvShowProposal proposal(long showId, String scraperId, String providerId) {
        TvShow show = requireShow(showId);
        TvShowResult scraped = inLanguageOf(showId, () -> fetch(scraper(scraperId), providerId));
        Set<EpisodeKey> known = scraped.episodes().stream().map(EpisodeKey::of).collect(Collectors.toSet());
        int matched = (int) episodes(show).stream().filter(episode -> known.contains(EpisodeKey.of(episode))).count();
        return new TvShowProposal(TvShowScrapeMerger.mergeShow(show.metadata(), scraped), matched,
                scraped.episodes().size());
    }

    /**
     * Fills empty details of the episodes and the TV show (actors, ratings, season titles) from the
     * hit and writes the affected NFOs. An NFO that cannot be written leaves the episodes of that
     * file unchanged.
     */
    @Transactional
    public EpisodeFillResult fillFromProvider(long showId, String scraperId, String providerId) {
        return inLanguageOf(showId, () -> fill(requireShow(showId), scraper(scraperId), providerId, false,
                EpisodeScope.all()));
    }

    /**
     * Fetches titles, texts, season titles and episode names again in the TV show's language and overwrites the
     * existing ones, including manually edited ones. What the provider does not have in that language stays. An NFO
     * that cannot be written leaves the episodes of that file unchanged.
     *
     * @throws TvShowProblemException if no provider is set up, it reports an error or it does not find the TV show
     *                                unambiguously
     */
    @Transactional
    public TextRefetch refetchTexts(long showId) {
        return inLanguageOf(showId, () -> {
            TvShowMetadata current = metadataService.currentMetadata(showId);
            TvShow show = requireShow(showId);
            TvShowScraper scraper = preferredReadyScraper().orElseThrow(TvShowProblemException::noScraper);
            TvShowResult scraped = identify(scraper, show);
            String providerId = scraped.uniqueIds().get(scraper.id());
            if (providerId == null) {
                throw TvShowProblemException.notIdentified(scraper, show.getTitle());
            }
            TvShowMetadata merged = TvShowScrapeMerger.mergeShow(current, scraped);
            boolean showChanged = !merged.equals(current);
            if (showChanged) {
                metadataService.updateShow(showId, merged);
                metadataService.storeLanguageInNfo(showId);
            }
            EpisodeFillResult filled = fill(show, scraper, providerId, true, EpisodeScope.all());
            return new TextRefetch(showChanged || filled.updated() > 0, filled.failedFiles());
        });
    }

    /**
     * @param changed     whether anything changed in the TV show or its episodes
     * @param failedFiles NFOs that could not be written
     */
    public record TextRefetch(boolean changed, List<String> failedFiles) {
    }

    private <T> T inLanguageOf(long showId, Supplier<T> body) {
        return ScrapeLanguage.with(metadataService.scrapeLanguage(showId), body);
    }

    /**
     * Fetches titles and texts of the episodes of a season or file again and replaces the existing ones, including
     * manually edited ones. Of the TV show at most the title of the season changes. What the provider does not have
     * stays.
     *
     * @throws TvShowProblemException if no provider is set up, it reports an error or the TV show is not yet
     *                                identified
     */
    @Transactional
    public TvShowAutoScrapeOutcome refetch(long showId, EpisodeScope scope) {
        return inLanguageOf(showId, () -> {
            TvShow show = requireShow(showId);
            TvShowScraper scraper = preferredReadyScraper().orElseThrow(TvShowProblemException::noScraper);
            if (knownIds(show.metadata()).isEmpty()) {
                throw TvShowProblemException.notIdentified(scraper, show.getTitle());
            }
            String providerId = identify(scraper, show).uniqueIds().get(scraper.id());
            if (providerId == null) {
                throw TvShowProblemException.notIdentified(scraper, show.getTitle());
            }
            EpisodeFillResult filled = fill(show, scraper, providerId, true, scope);
            return new TvShowAutoScrapeOutcome(TvShowAutoScrapeOutcome.Status.ALREADY_IDENTIFIED, filled.updated(),
                    filled.failedFiles());
        });
    }

    /**
     * @param overwrite whether existing titles and texts are replaced; otherwise only empty fields get values
     */
    private EpisodeFillResult fill(TvShow show, TvShowScraper scraper, String providerId, boolean overwrite,
            EpisodeScope scope) {
        TvShowResult scraped = fetch(scraper, providerId);
        List<String> failedFiles = new ArrayList<>();
        fillShowExtras(show, scraped, failedFiles, overwrite, scope);
        Map<EpisodeKey, EpisodeResult> byNumber = scraped.episodes().stream()
                .collect(Collectors.toMap(EpisodeKey::of, Function.identity(), (first, second) -> first));
        Map<String, List<Episode>> byFile = episodes(show).stream()
                .filter(scope::covers)
                .collect(Collectors.groupingBy(Episode::getFilePath, LinkedHashMap::new, Collectors.toList()));
        int updated = 0;
        int unchanged = 0;
        int unmatched = 0;
        for (List<Episode> sameFile : byFile.values()) {
            if (!refreshed(sameFile, failedFiles)) {
                continue;
            }
            Map<Episode, EpisodeFill> fills = new HashMap<>();
            for (Episode episode : sameFile) {
                EpisodeResult match = byNumber.get(EpisodeKey.of(episode));
                if (match == null) {
                    unmatched++;
                    continue;
                }
                match = withDetailsIfMissing(scraper, episode, match);
                (overwrite ? TvShowScrapeMerger.refreshEpisode(episode.metadata(), episode.extras(), match)
                        : TvShowScrapeMerger.fillEpisode(episode.metadata(), episode.extras(), match,
                                episode.getNfoModifiedAt() == null)).ifPresent(fill -> fills.put(episode, fill));
            }
            int matchedInFile = (int) sameFile.stream().filter(e -> byNumber.containsKey(EpisodeKey.of(e))).count();
            if (fills.isEmpty()) {
                unchanged += matchedInFile;
            } else if (writeEpisodes(sameFile, fills, failedFiles)) {
                updated += fills.size();
                unchanged += matchedInFile - fills.size();
            }
        }
        log.info("Filled episodes of TV show {} from {}: {} updated, {} unchanged, {} unmatched, {} failed files",
                show.getId(), scraper.id(), updated, unchanged, unmatched, failedFiles.size());
        return new EpisodeFillResult(updated, unchanged, unmatched, failedFiles);
    }

    /**
     * Scrapes a TV show automatically with the preferred TV show scraper: A TV show that is not yet
     * identified is only applied on an unambiguous hit (overlaid as with movies); afterwards the
     * episodes get empty fields filled.
     *
     * @throws TvShowProblemException if the provider reports an error or the NFO cannot be written
     */
    @Transactional
    public TvShowAutoScrapeOutcome scrapeMissing(long showId) {
        return scrapeMissing(showId, EpisodeScope.all());
    }

    /**
     * Like {@link #scrapeMissing(long)}, but enriches only the episodes of the scope; of the TV show at most the
     * title of the season. A TV show that is not yet identified is, as there, only applied on an unambiguous hit.
     *
     * @throws TvShowProblemException if the provider reports an error
     */
    @Transactional
    public TvShowAutoScrapeOutcome scrapeMissing(long showId, EpisodeScope scope) {
        return inLanguageOf(showId, () -> scrapeMissingInLanguage(showId, scope));
    }

    private TvShowAutoScrapeOutcome scrapeMissingInLanguage(long showId, EpisodeScope scope) {
        TvShowMetadata current = metadataService.currentMetadata(showId);
        TvShow show = requireShow(showId);
        Optional<TvShowScraper> preferred = preferredReadyScraper();
        if (preferred.isEmpty()) {
            return TvShowAutoScrapeOutcome.of(TvShowAutoScrapeOutcome.Status.NO_SCRAPER);
        }
        TvShowScraper scraper = preferred.get();
        Map<String, String> ids = knownIds(current);
        String providerId = ids.get(scraper.id());
        TvShowAutoScrapeOutcome.Status status = TvShowAutoScrapeOutcome.Status.ALREADY_IDENTIFIED;
        if (providerId == null) {
            ScrapeRequest request = new ScrapeRequest(MediaType.TV_SHOW, show.getTitle(), show.getReleaseYear(), ids);
            Optional<TvShowResult> scraped = call(scraper, s -> s.scrape(request));
            providerId = scraped.map(result -> result.uniqueIds().get(scraper.id())).orElse(null);
            if (providerId == null) {
                return TvShowAutoScrapeOutcome.of(TvShowAutoScrapeOutcome.Status.NO_MATCH);
            }
            cache.put(scraper.id(), providerId, scraped.get());
            metadataService.updateShow(showId, TvShowScrapeMerger.mergeShow(current, scraped.get()));
            metadataService.storeLanguageInNfo(showId);
            status = TvShowAutoScrapeOutcome.Status.SCRAPED;
        }
        EpisodeFillResult filled = fill(show, scraper, providerId, false, scope);
        return new TvShowAutoScrapeOutcome(status, filled.updated(), filled.failedFiles());
    }

    /** The configured TV show scraper, otherwise the first one set up. */
    private Optional<TvShowScraper> preferredReadyScraper() {
        List<TvShowScraper> ready = scraperRegistry.tvShowScrapers().stream()
                .filter(scraper -> scraper.missingConfiguration().isEmpty())
                .toList();
        return ready.stream()
                .filter(scraper -> scraper.id().equals(properties.preferredTvShowScraper()))
                .findFirst()
                .or(() -> ready.stream().findFirst());
    }

    /**
     * Episodes that the provider knows but for which there is no file.
     *
     * @throws TvShowProblemException if the TV show is not unambiguously identified at the provider
     */
    @Transactional(readOnly = true)
    public List<MissingEpisode> missingEpisodes(long showId, String scraperId) {
        TvShow show = requireShow(showId);
        TvShowResult scraped = inLanguageOf(showId, () -> identify(scraper(scraperId), show));
        Set<EpisodeKey> present = episodes(show).stream().map(EpisodeKey::of).collect(Collectors.toSet());
        return scraped.episodes().stream()
                .filter(episode -> !present.contains(EpisodeKey.of(episode)))
                .sorted(Comparator.comparingInt(EpisodeResult::season).thenComparingInt(EpisodeResult::episode))
                .map(episode -> new MissingEpisode(episode.season(), episode.episode(), episode.title(),
                        episode.aired()))
                .toList();
    }

    /**
     * Asks all set-up TV show scrapers for the still of the episode. A provider that does not know the
     * TV show or reports an error appears as a notice.
     */
    @Transactional(readOnly = true)
    public EpisodeThumbs episodeThumbs(long episodeId) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> TvShowProblemException.episodeNotFound(episodeId));
        TvShow show = requireShow(episode.getTvShowId());
        return inLanguageOf(show.getId(), () -> episodeThumbs(episode, show));
    }

    private EpisodeThumbs episodeThumbs(Episode episode, TvShow show) {
        List<EpisodeThumbs.Thumb> thumbs = new ArrayList<>();
        List<LocalizedText> warnings = new ArrayList<>();
        for (TvShowScraper scraper : scraperRegistry.tvShowScrapers()) {
            Optional<LocalizedText> missing = scraper.missingConfiguration();
            if (missing.isPresent()) {
                warnings.add(missing.get());
                continue;
            }
            try {
                identify(scraper, show).episodes().stream()
                        .filter(candidate -> EpisodeKey.of(candidate).equals(EpisodeKey.of(episode)))
                        .map(EpisodeResult::thumb)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .ifPresent(url -> thumbs.add(new EpisodeThumbs.Thumb(scraper.id(), scraper.displayName(), url)));
            } catch (TvShowProblemException e) {
                warnings.add(LocalizedText.of("tvShow.thumbs.scraperFailed").with("scraper", scraper.displayName())
                        .with("message", e.localizedText()));
            }
        }
        return new EpisodeThumbs(thumbs, warnings);
    }

    /** Reads an episode NFO changed outside KMM; an unreadable one is left untouched. */
    private boolean refreshed(List<Episode> sameFile, List<String> failedFiles) {
        try {
            nfoSync.refreshEpisodesIfChangedOutside(sameFile);
            return true;
        } catch (TvShowProblemException e) {
            log.warn("NFO of episode file {} not written: {}", sameFile.getFirst().getFilePath(), e.getMessage());
            failedFiles.add(sameFile.getFirst().getFilePath());
            return false;
        }
    }

    private void fillShowExtras(TvShow show, TvShowResult scraped, List<String> failedFiles, boolean overwrite,
            EpisodeScope scope) {
        if (scope.filePath() != null) {
            return;
        }
        try {
            nfoSync.refreshShowIfChangedOutside(show);
        } catch (TvShowProblemException e) {
            log.warn("NFO of TV show {} not written: {}", show.getId(), e.getMessage());
            failedFiles.add(TvShowFiles.tvShowNfo(show.directory()).toString());
            return;
        }
        TvShowExtras extras;
        if (scope.season() != null) {
            extras = TvShowScrapeMerger.withSeasonTitle(show.extras(), scraped, scope.season(), overwrite);
        } else {
            extras = overwrite ? TvShowScrapeMerger.refreshShowExtras(show.extras(), scraped)
                    : TvShowScrapeMerger.fillShowExtras(show.extras(), scraped);
        }
        if (extras.equals(show.extras())) {
            return;
        }
        try {
            TvNfoState written = nfoFiles.writeShow(show.directory(), TvNfoMapper.toNfo(show.metadata(), extras,
                    show.nfoTrailer()));
            show.applyExtras(extras);
            show.markNfoSynchronized(written.modifiedAt());
            events.publishEvent(new TvShowFilesChangedEvent(show.getId()));
        } catch (NfoException e) {
            log.warn("NFO of TV show {} not written: {}", show.getId(), e.getMessage());
            failedFiles.add(TvShowFiles.tvShowNfo(show.directory()).toString());
        }
    }

    /**
     * Requests the individual details of an episode (e.g. director and writer at TVDB) only if the
     * episode lacks them and the hit does not already contain them; every request burdens the provider. An
     * error there does not prevent applying the remaining details.
     */
    private static EpisodeResult withDetailsIfMissing(TvShowScraper scraper, Episode episode, EpisodeResult match) {
        EpisodeExtras extras = episode.extras();
        boolean missingInLibrary = extras.directors().isEmpty() || extras.writers().isEmpty();
        boolean missingInMatch = match.directors().isEmpty() && match.writers().isEmpty();
        if (!missingInLibrary || !missingInMatch) {
            return match;
        }
        try {
            return scraper.episodeDetails(match);
        } catch (ScraperException e) {
            log.warn("Details of episode S{}E{} not loaded from {}: {}", match.season(), match.episode(), scraper.id(),
                    e.getMessage());
            return match;
        }
    }

    /** Writes the shared NFO of a file with all its episodes; only then does the database change. */
    private boolean writeEpisodes(List<Episode> sameFile, Map<Episode, EpisodeFill> fills, List<String> failedFiles) {
        List<EpisodeNfo> nfos = sameFile.stream()
                .map(episode -> {
                    EpisodeFill fill = fills.get(episode);
                    return TvNfoMapper.toNfo(episode.getSeason(), episode.getEpisode(),
                            fill == null ? episode.metadata() : fill.metadata(),
                            fill == null ? episode.extras() : fill.extras(), episode.getAddedAt(), clock.getZone());
                })
                .toList();
        Episode first = sameFile.getFirst();
        try {
            TvNfoState written = nfoFiles.writeEpisodes(first.file(), nfos);
            fills.forEach((episode, fill) -> {
                episode.applyMetadata(fill.metadata());
                episode.applyExtras(fill.extras());
            });
            sameFile.forEach(episode -> {
                episode.markNfoSynchronized(written.modifiedAt());
                events.publishEvent(new EpisodeFilesChangedEvent(episode.getId()));
            });
            return true;
        } catch (NfoException e) {
            log.warn("NFO of {} not written: {}", first.getFilePath(), e.getMessage());
            failedFiles.add(first.getFilePath());
            return false;
        }
    }

    /** Via the ID known at this provider, otherwise via the scraper's identification. */
    private TvShowResult identify(TvShowScraper scraper, TvShow show) {
        Map<String, String> ids = knownIds(show.metadata());
        String providerId = ids.get(scraper.id());
        if (providerId != null) {
            return fetch(scraper, providerId);
        }
        ScrapeRequest request = new ScrapeRequest(MediaType.TV_SHOW, show.getTitle(), show.getReleaseYear(), ids);
        TvShowResult scraped = call(scraper, s -> s.scrape(request))
                .orElseThrow(() -> TvShowProblemException.notIdentified(scraper, show.getTitle()));
        Optional.ofNullable(scraped.uniqueIds().get(scraper.id()))
                .ifPresent(id -> cache.put(scraper.id(), id, scraped));
        return scraped;
    }

    private TvShowResult fetch(TvShowScraper scraper, String providerId) {
        return cache.get(scraper.id(), providerId).orElseGet(() -> {
            TvShowResult scraped = call(scraper, s -> s.fetch(providerId))
                    .orElseThrow(() -> TvShowProblemException.notFoundAtProvider(scraper, providerId));
            cache.put(scraper.id(), providerId, scraped);
            return scraped;
        });
    }

    private static Map<String, String> knownIds(TvShowMetadata metadata) {
        Map<String, String> ids = new HashMap<>();
        Optional.ofNullable(metadata.tvdbId()).ifPresent(id -> ids.put(TvShowScrapeMerger.TVDB, id));
        Optional.ofNullable(metadata.tmdbId()).ifPresent(id -> ids.put(TvShowScrapeMerger.TMDB, id));
        Optional.ofNullable(metadata.imdbId()).ifPresent(id -> ids.put(TvShowScrapeMerger.IMDB, id));
        return ids;
    }

    private List<Episode> episodes(TvShow show) {
        return episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(show.getId());
    }

    private TvShowScraper scraper(String scraperId) {
        TvShowScraper scraper = scraperRegistry.tvShowScraper(scraperId)
                .orElseThrow(() -> TvShowProblemException.unknownScraper(scraperId));
        scraper.missingConfiguration().ifPresent(hint -> {
            throw TvShowProblemException.scraperNotConfigured(scraper, hint);
        });
        return scraper;
    }

    private TvShow requireShow(long showId) {
        return showRepository.findById(showId).orElseThrow(() -> TvShowProblemException.showNotFound(showId));
    }

    private static <T> T call(TvShowScraper scraper, Function<TvShowScraper, T> request) {
        try {
            return request.apply(scraper);
        } catch (ScraperException e) {
            throw TvShowProblemException.providerFailed(scraper, e);
        }
    }

    private record EpisodeKey(int season, int episode) {

        static EpisodeKey of(Episode episode) {
            return new EpisodeKey(episode.getSeason(), episode.getEpisode());
        }

        static EpisodeKey of(EpisodeResult episode) {
            return new EpisodeKey(episode.season(), episode.episode());
        }
    }
}
