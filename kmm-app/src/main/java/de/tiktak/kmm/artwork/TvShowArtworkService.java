package de.tiktak.kmm.artwork;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScrapeLanguage;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.TvShowFiles;
import de.tiktak.kmm.tvshow.EpisodeDetails;
import de.tiktak.kmm.tvshow.EpisodeFilesChangedEvent;
import de.tiktak.kmm.tvshow.EpisodeSummary;
import de.tiktak.kmm.tvshow.EpisodeThumbs;
import de.tiktak.kmm.tvshow.TvShowDetails;
import de.tiktak.kmm.tvshow.TvShowFilesChangedEvent;
import de.tiktak.kmm.tvshow.TvShowMetadata;
import de.tiktak.kmm.tvshow.TvShowMetadataService;
import de.tiktak.kmm.tvshow.TvShowScrapeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bilder von Serien, Staffeln und Episoden nach Kodi-Konvention: {@code poster.jpg} im Serienordner,
 * {@code season01-poster.jpg} für Staffeln und {@code <episode>-thumb.jpg} neben der Episode. Die
 * Bilder liegen nur als Dateien vor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TvShowArtworkService {

    private static final String TVDB = "tvdb";
    private static final String TMDB = "tmdb";
    private static final String IMDB = "imdb";
    private static final String SHOW_TARGET = "Serien";
    private static final String SEASON_TARGET = "Staffeln";
    private static final String EPISODE_TARGET = "Episoden";

    private final TvShowMetadataService metadataService;
    private final TvShowScrapeService scrapeService;
    private final ArtworkFiles artworkFiles;
    private final ArtworkCandidateFinder candidateFinder;
    private final ImageDownloader downloader;
    private final ApplicationEventPublisher events;

    /**
     * @param season Staffel oder {@code null} für die Bilder der Serie
     */
    public List<ArtworkInfo> artwork(long showId, Integer season) {
        Path directory = directory(metadataService.findShow(showId));
        return types(season).stream()
                .map(type -> ArtworkInfo.of(type, artworkFiles.find(slot(directory, type, season))))
                .toList();
    }

    ArtworkFile image(long showId, ImageType type, Integer season) {
        requireSupported(type, season);
        return artworkFiles.find(slot(directory(metadataService.findShow(showId)), type, season))
                .orElseThrow(() -> ArtworkProblemException.imageMissing(type));
    }

    /** Die Poster-Datei der Serie, etwa für den Export. */
    public Optional<Path> posterFile(TvShowDetails show) {
        return artworkFiles.find(slot(directory(show), ImageType.POSTER, null)).map(ArtworkFile::file);
    }

    public ArtworkCandidates candidates(long showId, ImageType type, Integer season) {
        requireSupported(type, season);
        TvShowMetadata metadata = metadataService.findShow(showId).metadata();
        Map<String, String> uniqueIds = uniqueIds(metadata);
        if (uniqueIds.isEmpty()) {
            throw ArtworkProblemException.missingTvIds();
        }
        ArtworkSearch search = ScrapeLanguage.with(metadataService.scrapeLanguage(showId),
                () -> candidateFinder.findAll(MediaType.TV_SHOW, metadata.title(), metadata.year(), uniqueIds));
        return new ArtworkCandidates(search.candidatesOf(type, season), search.warnings());
    }

    public ArtworkInfo apply(long showId, ImageType type, Integer season, String url) {
        requireSupported(type, season);
        ArtworkSlot slot = slot(directory(metadataService.findShow(showId)), type, season);
        ArtworkFile saved = artworkFiles.save(slot, downloader.download(parse(url)));
        log.info("Saved {} (season {}) of TV show {} to {}", type, season, showId, saved.file());
        events.publishEvent(new TvShowFilesChangedEvent(showId));
        return ArtworkInfo.of(type, Optional.of(saved));
    }

    public void remove(long showId, ImageType type, Integer season) {
        requireSupported(type, season);
        artworkFiles.delete(slot(directory(metadataService.findShow(showId)), type, season));
        events.publishEvent(new TvShowFilesChangedEvent(showId));
    }

    /**
     * Holt für jede fehlende Bildart der Serie, ihrer Staffeln mit Episoden und für fehlende Standbilder
     * den besten Treffer. Vorhandene Bilder bleiben unverändert.
     *
     * @return Anzahl gespeicherter Bilder und Hinweise; ohne TVDB-, TMDB- oder IMDb-ID wird nichts gesucht
     */
    public ArtworkFillResult fillMissing(long showId) {
        TvShowDetails show = metadataService.findShow(showId);
        Path directory = directory(show);
        Map<String, String> uniqueIds = uniqueIds(show.metadata());
        if (uniqueIds.isEmpty()) {
            return new ArtworkFillResult(0, List.of());
        }
        List<Integer> seasons = show.seasons().stream()
                .filter(season -> season.episodeCount() > 0)
                .map(TvShowDetails.Season::number)
                .toList();
        List<String> warnings = new ArrayList<>();
        int saved = 0;
        ArtworkSearch search = null;
        for (Integer season : withShow(seasons)) {
            for (ImageType type : types(season)) {
                ArtworkSlot slot = slot(directory, type, season);
                if (artworkFiles.find(slot).isPresent()) {
                    continue;
                }
                if (search == null) {
                    search = ScrapeLanguage.with(metadataService.scrapeLanguage(showId),
                            () -> candidateFinder.findAll(MediaType.TV_SHOW, show.metadata().title(),
                                    show.metadata().year(), uniqueIds));
                    warnings.addAll(search.warnings());
                }
                if (ArtworkDownloads.saveFirstLoadable(downloader, type, search.candidatesOf(type, season),
                        image -> artworkFiles.save(slot, image), warnings)) {
                    saved++;
                }
            }
        }
        if (saved > 0) {
            events.publishEvent(new TvShowFilesChangedEvent(showId));
        }
        saved += fillMissingThumbs(show, warnings);
        return new ArtworkFillResult(saved, warnings);
    }

    private int fillMissingThumbs(TvShowDetails show, List<String> warnings) {
        int saved = 0;
        for (EpisodeSummary episode : show.episodes()) {
            ArtworkSlot slot = episodeSlot(Path.of(episode.filePath()), ImageType.THUMB);
            if (artworkFiles.find(slot).isPresent()) {
                continue;
            }
            EpisodeThumbs thumbs = scrapeService.episodeThumbs(episode.id());
            warnings.addAll(thumbs.warnings());
            List<ArtworkCandidate> candidates = thumbs.thumbs().stream()
                    .map(thumb -> new ArtworkCandidate(thumb.scraperId(), thumb.scraperName(), ImageType.THUMB,
                            thumb.url(), thumb.url(), null, null, null, null, null))
                    .toList();
            if (ArtworkDownloads.saveFirstLoadable(downloader, ImageType.THUMB, candidates,
                    image -> artworkFiles.save(slot, image), warnings)) {
                saved++;
                events.publishEvent(new EpisodeFilesChangedEvent(episode.id()));
            }
        }
        return saved;
    }

    /** Erst die Serie ({@code null}), dann die Staffeln. */
    private static List<Integer> withShow(List<Integer> seasons) {
        List<Integer> targets = new ArrayList<>();
        targets.add(null);
        targets.addAll(seasons);
        return targets;
    }

    public List<ArtworkInfo> episodeArtwork(long episodeId) {
        Path file = episodeFile(metadataService.findEpisode(episodeId));
        return ArtworkTypes.EPISODE_TYPES.stream()
                .map(type -> ArtworkInfo.of(type, artworkFiles.find(episodeSlot(file, type))))
                .toList();
    }

    ArtworkFile episodeImage(long episodeId, ImageType type) {
        requireEpisodeType(type);
        return artworkFiles.find(episodeSlot(episodeFile(metadataService.findEpisode(episodeId)), type))
                .orElseThrow(() -> ArtworkProblemException.imageMissing(type));
    }

    /** Die Standbilder kommen von den Serien-Scrapern, die die Episode kennen. */
    public ArtworkCandidates episodeCandidates(long episodeId, ImageType type) {
        requireEpisodeType(type);
        EpisodeThumbs thumbs = scrapeService.episodeThumbs(episodeId);
        List<ArtworkCandidate> candidates = thumbs.thumbs().stream()
                .map(thumb -> new ArtworkCandidate(thumb.scraperId(), thumb.scraperName(), type, thumb.url(),
                        thumb.url(), null, null, null, null, null))
                .toList();
        return new ArtworkCandidates(candidates, thumbs.warnings());
    }

    public ArtworkInfo applyEpisode(long episodeId, ImageType type, String url) {
        requireEpisodeType(type);
        ArtworkSlot slot = episodeSlot(episodeFile(metadataService.findEpisode(episodeId)), type);
        ArtworkFile saved = artworkFiles.save(slot, downloader.download(parse(url)));
        log.info("Saved {} of episode {} to {}", type, episodeId, saved.file());
        events.publishEvent(new EpisodeFilesChangedEvent(episodeId));
        return ArtworkInfo.of(type, Optional.of(saved));
    }

    public void removeEpisode(long episodeId, ImageType type) {
        requireEpisodeType(type);
        artworkFiles.delete(episodeSlot(episodeFile(metadataService.findEpisode(episodeId)), type));
        events.publishEvent(new EpisodeFilesChangedEvent(episodeId));
    }

    private static ArtworkSlot slot(Path directory, ImageType type, Integer season) {
        String kodiName = ArtworkTypes.kodiName(type);
        return new ArtworkSlot(type, season == null
                ? TvShowFiles.tvShowArtworkCandidates(directory, kodiName)
                : TvShowFiles.seasonArtworkCandidates(directory, season, kodiName));
    }

    private static ArtworkSlot episodeSlot(Path episodeFile, ImageType type) {
        return new ArtworkSlot(type, TvShowFiles.episodeThumbCandidates(episodeFile));
    }

    private static List<ImageType> types(Integer season) {
        return season == null ? ArtworkTypes.TV_SHOW_TYPES : ArtworkTypes.SEASON_TYPES;
    }

    private static Map<String, String> uniqueIds(TvShowMetadata metadata) {
        Map<String, String> ids = new LinkedHashMap<>();
        Optional.ofNullable(metadata.tvdbId()).ifPresent(id -> ids.put(TVDB, id));
        Optional.ofNullable(metadata.tmdbId()).ifPresent(id -> ids.put(TMDB, id));
        Optional.ofNullable(metadata.imdbId()).ifPresent(id -> ids.put(IMDB, id));
        return ids;
    }

    private static Path directory(TvShowDetails show) {
        return Path.of(show.directoryPath());
    }

    private static Path episodeFile(EpisodeDetails episode) {
        return Path.of(episode.filePath());
    }

    private static URI parse(String url) {
        try {
            return URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw ImageDownloadException.invalidUrl(url);
        }
    }

    private static void requireSupported(ImageType type, Integer season) {
        if (!types(season).contains(type)) {
            throw ArtworkProblemException.unsupportedTvType(type, season == null ? SHOW_TARGET : SEASON_TARGET);
        }
    }

    private static void requireEpisodeType(ImageType type) {
        if (!ArtworkTypes.EPISODE_TYPES.contains(type)) {
            throw ArtworkProblemException.unsupportedTvType(type, EPISODE_TARGET);
        }
    }
}
