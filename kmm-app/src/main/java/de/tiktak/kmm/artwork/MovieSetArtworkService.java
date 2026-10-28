package de.tiktak.kmm.artwork;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.movie.MovieSetChangedEvent;
import de.tiktak.kmm.movie.MovieSetData;
import de.tiktak.kmm.movie.MovieSetService;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.settings.MovieSetSettings;
import de.tiktak.kmm.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bilder eines Filmsets: anzeigen, über die TMDB-ID der Sammlung online suchen, übernehmen und entfernen. Wo die
 * Bilder liegen, legen die Einstellungen fest ({@link MovieSetSettings}). Jede Änderung wird als
 * {@link MovieSetChangedEvent} veröffentlicht, damit Kodi sie übernimmt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieSetArtworkService {

    private static final String TMDB = "tmdb";

    private final MovieSetService movieSetService;
    private final SettingsService settingsService;
    private final MovieSetArtworkFiles artworkFiles;
    private final ArtworkCandidateFinder candidateFinder;
    private final ImageDownloader downloader;
    private final ApplicationEventPublisher events;

    /** Ohne eingeschaltete Ablage gilt jedes Bild als fehlend. */
    public List<ArtworkInfo> artwork(long setId) {
        String title = data(setId).name();
        MovieSetSettings settings = settings();
        return ArtworkTypes.MOVIE_SET_TYPES.stream()
                .map(type -> ArtworkInfo.of(type, artworkFiles.find(settings, title, type)))
                .toList();
    }

    ArtworkFile image(long setId, ImageType type) {
        requireSupported(type);
        return artworkFiles.find(settings(), data(setId).name(), type)
                .orElseThrow(() -> ArtworkProblemException.imageMissing(type));
    }

    public ArtworkCandidates candidates(long setId, ImageType type) {
        requireSupported(type);
        MovieSetData data = data(setId);
        if (data.tmdbCollectionId() == null) {
            throw ArtworkProblemException.missingCollectionId();
        }
        ArtworkSearch search = candidateFinder.findAll(MediaType.MOVIE_SET, data.name(), null,
                Map.of(TMDB, data.tmdbCollectionId()));
        return new ArtworkCandidates(search.candidatesOf(type), search.warnings());
    }

    /**
     * Lädt das Bild und speichert es in jeder eingeschalteten Ablage.
     */
    public ArtworkInfo apply(long setId, ImageType type, String url) {
        requireSupported(type);
        MovieSetSettings settings = requireStorage();
        String title = data(setId).name();
        ArtworkFile saved = artworkFiles.save(settings, title, type, downloader.download(parse(url)));
        log.info("Saved {} of movie set {} to {}", type, setId, saved.file());
        events.publishEvent(new MovieSetChangedEvent(setId, title));
        return ArtworkInfo.of(type, Optional.of(saved));
    }

    /**
     * Holt für jede fehlende Bildart den besten Treffer; vorhandene Bilder bleiben unverändert.
     *
     * @return Anzahl gespeicherter Bilder und Hinweise; ohne Ablage oder TMDB-ID wird nichts gesucht
     */
    public ArtworkFillResult fillMissing(long setId) {
        MovieSetSettings settings = settings();
        MovieSetData data = data(setId);
        if (!MovieSetArtworkFiles.hasStorage(settings) || data.tmdbCollectionId() == null) {
            return new ArtworkFillResult(0, List.of());
        }
        List<ImageType> missing = ArtworkTypes.MOVIE_SET_TYPES.stream()
                .filter(type -> artworkFiles.find(settings, data.name(), type).isEmpty())
                .toList();
        if (missing.isEmpty()) {
            return new ArtworkFillResult(0, List.of());
        }
        ArtworkSearch search = candidateFinder.findAll(MediaType.MOVIE_SET, data.name(), null,
                Map.of(TMDB, data.tmdbCollectionId()));
        List<String> warnings = new ArrayList<>(search.warnings());
        int saved = 0;
        for (ImageType type : missing) {
            if (ArtworkDownloads.saveFirstLoadable(downloader, type, search.candidatesOf(type),
                    image -> artworkFiles.save(settings, data.name(), type, image), warnings)) {
                saved++;
            }
        }
        if (saved > 0) {
            events.publishEvent(new MovieSetChangedEvent(setId, data.name()));
        }
        return new ArtworkFillResult(saved, warnings);
    }

    public void remove(long setId, ImageType type) {
        requireSupported(type);
        MovieSetSettings settings = requireStorage();
        String title = data(setId).name();
        artworkFiles.delete(settings, title, type);
        events.publishEvent(new MovieSetChangedEvent(setId, title));
    }

    /** Verschiebt die Bilder eines umbenannten Sets in allen Ablagen auf den neuen Titel. */
    void moveToNewTitle(long setId, String previousTitle) {
        MovieSetSettings settings = settings();
        String title = data(setId).name();
        if (MovieSetArtworkFiles.hasStorage(settings) && !title.equals(previousTitle)) {
            artworkFiles.rename(settings, previousTitle, title, ArtworkTypes.MOVIE_SET_TYPES);
        }
    }

    private MovieSetData data(long setId) {
        return movieSetService.find(setId).data();
    }

    private MovieSetSettings settings() {
        return settingsService.current().movieSets();
    }

    private MovieSetSettings requireStorage() {
        MovieSetSettings settings = settings();
        if (!MovieSetArtworkFiles.hasStorage(settings)) {
            throw ArtworkProblemException.noMovieSetImageStorage();
        }
        return settings;
    }

    private static URI parse(String url) {
        try {
            return URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw ImageDownloadException.invalidUrl(url);
        }
    }

    private static void requireSupported(ImageType type) {
        if (!ArtworkTypes.MOVIE_SET_TYPES.contains(type)) {
            throw ArtworkProblemException.unsupportedTvType(type, "Filmsets");
        }
    }
}
