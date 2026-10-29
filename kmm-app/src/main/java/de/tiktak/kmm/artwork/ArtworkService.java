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

import de.tiktak.kmm.movie.MovieDetails;
import de.tiktak.kmm.movie.MovieFilesChangedEvent;
import de.tiktak.kmm.movie.MovieMetadata;
import de.tiktak.kmm.movie.MovieMetadataService;
import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.CompanionFiles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bilder eines Films: anzeigen, online suchen, übernehmen und entfernen. Die Bilder liegen als
 * Dateien neben dem Film; KMM speichert sie nicht in der Datenbank. Jede Änderung wird als
 * {@link MovieFilesChangedEvent} veröffentlicht.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtworkService {

    private static final String TMDB = "tmdb";
    private static final String IMDB = "imdb";

    private final MovieMetadataService metadataService;
    private final MovieArtworkFiles artworkFiles;
    private final ArtworkCandidateFinder candidateFinder;
    private final ImageDownloader downloader;
    private final ApplicationEventPublisher events;

    public List<ArtworkInfo> artwork(long movieId) {
        CompanionFiles files = companionFiles(metadataService.findDetails(movieId));
        return ArtworkTypes.MOVIE_TYPES.stream()
                .map(type -> ArtworkInfo.of(type, artworkFiles.find(files, type)))
                .toList();
    }

    ArtworkFile image(long movieId, ImageType type) {
        requireSupported(type);
        return artworkFiles.find(companionFiles(metadataService.findDetails(movieId)), type)
                .orElseThrow(() -> ArtworkProblemException.imageMissing(type));
    }

    /** Die Poster-Datei des Films, etwa für den Export. */
    public Optional<Path> posterFile(MovieDetails movie) {
        return artworkFiles.find(companionFiles(movie), ImageType.POSTER).map(ArtworkFile::file);
    }

    public ArtworkCandidates candidates(long movieId, ImageType type) {
        requireSupported(type);
        MovieMetadata metadata = metadataService.findDetails(movieId).metadata();
        Map<String, String> uniqueIds = uniqueIds(metadata);
        if (uniqueIds.isEmpty()) {
            throw ArtworkProblemException.missingIds();
        }
        return candidateFinder.find(type, metadata.title(), metadata.year(), uniqueIds);
    }

    /**
     * Lädt das Bild und speichert es nach Kodi-Konvention neben dem Film.
     */
    public ArtworkInfo apply(long movieId, ImageType type, String url) {
        requireSupported(type);
        CompanionFiles files = companionFiles(metadataService.findDetails(movieId));
        DownloadedImage image = downloader.download(parse(url));
        ArtworkFile saved = artworkFiles.save(files, type, image);
        log.info("Saved {} of movie {} to {}", type, movieId, saved.file());
        events.publishEvent(new MovieFilesChangedEvent(movieId));
        return ArtworkInfo.of(type, Optional.of(saved));
    }

    /**
     * Holt für jede fehlende Bildart den besten Treffer. Vorhandene Bilder bleiben unverändert;
     * schlägt ein Download fehl, wird der nächste Treffer versucht.
     *
     * @return Anzahl gespeicherter Bilder und Hinweise; ohne TMDB-/IMDb-ID wird nichts gesucht
     */
    public ArtworkFillResult fillMissing(long movieId) {
        MovieDetails movie = metadataService.findDetails(movieId);
        CompanionFiles files = companionFiles(movie);
        List<ImageType> missing = ArtworkTypes.MOVIE_TYPES.stream()
                .filter(type -> artworkFiles.find(files, type).isEmpty())
                .toList();
        Map<String, String> uniqueIds = uniqueIds(movie.metadata());
        if (missing.isEmpty() || uniqueIds.isEmpty()) {
            return new ArtworkFillResult(0, List.of());
        }
        ArtworkSearch search = candidateFinder.findAll(MediaType.MOVIE, movie.metadata().title(),
                movie.metadata().year(), uniqueIds);
        List<String> warnings = new ArrayList<>(search.warnings());
        int saved = 0;
        for (ImageType type : missing) {
            if (ArtworkDownloads.saveFirstLoadable(downloader, type, search.candidatesOf(type),
                    image -> artworkFiles.save(files, type, image), warnings)) {
                saved++;
            }
        }
        if (saved > 0) {
            events.publishEvent(new MovieFilesChangedEvent(movieId));
        }
        return new ArtworkFillResult(saved, warnings);
    }

    public void remove(long movieId, ImageType type) {
        requireSupported(type);
        artworkFiles.delete(companionFiles(metadataService.findDetails(movieId)), type);
        events.publishEvent(new MovieFilesChangedEvent(movieId));
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

    private static CompanionFiles companionFiles(MovieDetails movie) {
        return CompanionFiles.of(Path.of(movie.filePath()), movie.discFormat(), movie.partCount());
    }

    private static URI parse(String url) {
        try {
            return URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            throw ImageDownloadException.invalidUrl(url);
        }
    }

    private static void requireSupported(ImageType type) {
        if (!ArtworkTypes.MOVIE_TYPES.contains(type)) {
            throw ArtworkProblemException.unsupportedType(type);
        }
    }
}
