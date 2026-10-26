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
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoException;
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
 * Online-Suche für Serien: sucht beim Serien-Scraper, schlägt Metadaten für das Formular vor, ergänzt
 * leere Angaben der Episoden und ermittelt fehlende Episoden.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowScrapeService {

    private final ScraperRegistry scraperRegistry;
    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final TvShowNfoFiles nfoFiles;
    private final TvShowResultCache cache;
    private final TvShowMetadataService metadataService;
    private final TvScrapingProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * @param title Suchbegriff; ohne Angabe der Titel der Serie
     * @param year  Jahr; ohne Angabe das Jahr der Serie, sofern der Titel nicht angegeben wurde
     */
    @Transactional(readOnly = true)
    public List<TvShowScrapeCandidate> search(long showId, String scraperId, String title, Integer year) {
        TvShow show = requireShow(showId);
        boolean customTitle = title != null && !title.isBlank();
        SearchQuery query = new SearchQuery(MediaType.TV_SHOW, customTitle ? title : show.getTitle(),
                customTitle ? year : show.getReleaseYear());
        TvShowScraper scraper = scraper(scraperId);
        return call(scraper, s -> s.search(query)).stream().map(TvShowScrapeCandidate::from).toList();
    }

    @Transactional(readOnly = true)
    public TvShowProposal proposal(long showId, String scraperId, String providerId) {
        TvShow show = requireShow(showId);
        TvShowResult scraped = fetch(scraper(scraperId), providerId);
        Set<EpisodeKey> known = scraped.episodes().stream().map(EpisodeKey::of).collect(Collectors.toSet());
        int matched = (int) episodes(show).stream().filter(episode -> known.contains(EpisodeKey.of(episode))).count();
        return new TvShowProposal(TvShowScrapeMerger.mergeShow(show.metadata(), scraped), matched,
                scraped.episodes().size());
    }

    /**
     * Ergänzt leere Angaben der Episoden und der Serie (Darsteller, Bewertungen, Staffeltitel) aus dem
     * Treffer und schreibt die betroffenen NFOs. Eine NFO, die nicht geschrieben werden kann, lässt die
     * Episoden dieser Datei unverändert.
     */
    @Transactional
    public EpisodeFillResult fillFromProvider(long showId, String scraperId, String providerId) {
        return fill(requireShow(showId), scraper(scraperId), providerId);
    }

    private EpisodeFillResult fill(TvShow show, TvShowScraper scraper, String providerId) {
        TvShowResult scraped = fetch(scraper, providerId);
        List<String> failedFiles = new ArrayList<>();
        fillShowExtras(show, scraped, failedFiles);
        Map<EpisodeKey, EpisodeResult> byNumber = scraped.episodes().stream()
                .collect(Collectors.toMap(EpisodeKey::of, Function.identity(), (first, second) -> first));
        Map<String, List<Episode>> byFile = episodes(show).stream()
                .collect(Collectors.groupingBy(Episode::getFilePath, LinkedHashMap::new, Collectors.toList()));
        int updated = 0;
        int unchanged = 0;
        int unmatched = 0;
        for (List<Episode> sameFile : byFile.values()) {
            Map<Episode, EpisodeFill> fills = new HashMap<>();
            for (Episode episode : sameFile) {
                EpisodeResult match = byNumber.get(EpisodeKey.of(episode));
                if (match == null) {
                    unmatched++;
                    continue;
                }
                match = withDetailsIfMissing(scraper, episode, match);
                TvShowScrapeMerger.fillEpisode(episode.metadata(), episode.extras(), match,
                        episode.getNfoModifiedAt() == null).ifPresent(fill -> fills.put(episode, fill));
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
     * Scrapt eine Serie automatisch mit dem bevorzugten Serien-Scraper: Eine noch nicht zugeordnete
     * Serie wird nur bei eindeutigem Treffer übernommen (wie bei Filmen überlagert); danach bekommen die
     * Episoden leere Felder gefüllt.
     *
     * @throws TvShowProblemException wenn der Anbieter einen Fehler meldet oder die NFO nicht geschrieben werden kann
     */
    @Transactional
    public TvShowAutoScrapeOutcome scrapeMissing(long showId) {
        TvShow show = requireShow(showId);
        Optional<TvShowScraper> preferred = preferredReadyScraper();
        if (preferred.isEmpty()) {
            return TvShowAutoScrapeOutcome.of(TvShowAutoScrapeOutcome.Status.NO_SCRAPER);
        }
        TvShowScraper scraper = preferred.get();
        Map<String, String> ids = knownIds(show.metadata());
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
            metadataService.updateShow(showId, TvShowScrapeMerger.mergeShow(show.metadata(), scraped.get()));
            status = TvShowAutoScrapeOutcome.Status.SCRAPED;
        }
        EpisodeFillResult filled = fill(show, scraper, providerId);
        return new TvShowAutoScrapeOutcome(status, filled.updated(), filled.failedFiles());
    }

    /** Der konfigurierte Serien-Scraper, sonst der erste eingerichtete. */
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
     * Episoden, die der Anbieter kennt, für die es aber keine Datei gibt.
     *
     * @throws TvShowProblemException wenn die Serie beim Anbieter nicht eindeutig zugeordnet ist
     */
    @Transactional(readOnly = true)
    public List<MissingEpisode> missingEpisodes(long showId, String scraperId) {
        TvShow show = requireShow(showId);
        TvShowScraper scraper = scraper(scraperId);
        TvShowResult scraped = identify(scraper, show);
        Set<EpisodeKey> present = episodes(show).stream().map(EpisodeKey::of).collect(Collectors.toSet());
        return scraped.episodes().stream()
                .filter(episode -> !present.contains(EpisodeKey.of(episode)))
                .sorted(Comparator.comparingInt(EpisodeResult::season).thenComparingInt(EpisodeResult::episode))
                .map(episode -> new MissingEpisode(episode.season(), episode.episode(), episode.title(),
                        episode.aired()))
                .toList();
    }

    /**
     * Fragt alle eingerichteten Serien-Scraper nach dem Standbild der Episode. Ein Anbieter, der die
     * Serie nicht kennt oder einen Fehler meldet, erscheint als Hinweis.
     */
    @Transactional(readOnly = true)
    public EpisodeThumbs episodeThumbs(long episodeId) {
        Episode episode = episodeRepository.findById(episodeId)
                .orElseThrow(() -> TvShowProblemException.episodeNotFound(episodeId));
        TvShow show = requireShow(episode.getTvShowId());
        List<EpisodeThumbs.Thumb> thumbs = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (TvShowScraper scraper : scraperRegistry.tvShowScrapers()) {
            Optional<String> missing = scraper.missingConfiguration();
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
                warnings.add("%s: %s".formatted(scraper.displayName(), e.getBody().getDetail()));
            }
        }
        return new EpisodeThumbs(thumbs, warnings);
    }

    private void fillShowExtras(TvShow show, TvShowResult scraped, List<String> failedFiles) {
        TvShowExtras extras = TvShowScrapeMerger.fillShowExtras(show.extras(), scraped);
        if (extras.equals(show.extras())) {
            return;
        }
        try {
            TvNfoState written = nfoFiles.writeShow(show.directory(), TvNfoMapper.toNfo(show.metadata(), extras));
            show.applyExtras(extras);
            show.markNfoSynchronized(written.modifiedAt());
            events.publishEvent(new TvShowFilesChangedEvent(show.getId()));
        } catch (NfoException e) {
            log.warn("NFO of TV show {} not written: {}", show.getId(), e.getMessage());
            failedFiles.add(TvShowFiles.tvShowNfo(show.directory()).toString());
        }
    }

    /**
     * Fragt die Einzelangaben einer Episode (z. B. Regie und Drehbuch bei TVDB) nur an, wenn sie der
     * Episode fehlen und der Treffer sie nicht schon enthält; jede Anfrage belastet den Anbieter. Ein
     * Fehler dabei verhindert die Übernahme der übrigen Angaben nicht.
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

    /** Schreibt die gemeinsame NFO einer Datei mit allen ihren Episoden; erst danach ändert sich die Datenbank. */
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

    /** Über die bei diesem Anbieter bekannte ID, sonst über die Zuordnung des Scrapers. */
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
