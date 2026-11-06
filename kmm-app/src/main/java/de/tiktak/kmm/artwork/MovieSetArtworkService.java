package de.tiktak.kmm.artwork;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.movie.MovieSetArtworkFiller;
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
 * Images of a movie set: show, search online via the TMDB ID of the collection, apply and remove. Where the
 * images are stored is determined by the settings ({@link MovieSetSettings}). Every change is published as a
 * {@link MovieSetChangedEvent} so that Kodi picks it up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MovieSetArtworkService implements MovieSetArtworkFiller {

    private static final String TMDB = "tmdb";

    private final MovieSetService movieSetService;
    private final SettingsService settingsService;
    private final MovieSetArtworkFiles artworkFiles;
    private final ArtworkCandidateFinder candidateFinder;
    private final ImageDownloader downloader;
    private final ApplicationEventPublisher events;

    /** Without an enabled storage location every image counts as missing. */
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
     * Downloads the image and saves it in every enabled storage location.
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
     * Fetches the best match for each missing image type; existing images stay unchanged.
     *
     * @return number of saved images and notices; without a storage location or TMDB ID nothing is searched
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
        List<LocalizedText> warnings = new ArrayList<>(search.warnings());
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

    @Override
    public Fill fillMissingArtwork(long setId) {
        ArtworkFillResult result = fillMissing(setId);
        return new Fill(result.saved(), result.warnings());
    }

    public void remove(long setId, ImageType type) {
        requireSupported(type);
        MovieSetSettings settings = requireStorage();
        String title = data(setId).name();
        artworkFiles.delete(settings, title, type);
        events.publishEvent(new MovieSetChangedEvent(setId, title));
    }

    /**
     * The images of the set for Kodi, per managed image type the existing file or {@code null} for a missing
     * image.
     *
     * @return empty if no storage location is enabled; then KMM manages no set images
     */
    public Optional<List<MovieSetImage>> imagesForKodi(long setId) {
        MovieSetSettings settings = settings();
        if (!MovieSetArtworkFiles.hasStorage(settings)) {
            return Optional.empty();
        }
        String title = data(setId).name();
        return Optional.of(ArtworkTypes.MOVIE_SET_TYPES.stream()
                .map(type -> new MovieSetImage(ArtworkTypes.kodiName(type),
                        artworkFiles.find(settings, title, type).map(ArtworkFile::file).orElse(null)))
                .toList());
    }

    /** Moves the images of a renamed set to the new title in all storage locations. */
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
            throw ArtworkProblemException.unsupportedTvType(type, ArtworkProblemException.Target.MOVIE_SET);
        }
    }
}
