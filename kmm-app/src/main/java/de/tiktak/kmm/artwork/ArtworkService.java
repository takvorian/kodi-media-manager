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

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.ScrapeLanguage;
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
 * Images of a movie: show, search online, apply and remove. The images are stored as
 * files next to the movie; KMM does not store them in the database. Every change is published as a
 * {@link MovieFilesChangedEvent}.
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

    /** The poster file of the movie, e.g. for the export. */
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
        return ScrapeLanguage.with(metadataService.scrapeLanguage(movieId),
                () -> candidateFinder.find(type, metadata.title(), metadata.year(), uniqueIds));
    }

    /**
     * Downloads the image and saves it next to the movie following the Kodi convention.
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
     * Fetches the best match for each missing image type. Existing images stay unchanged;
     * if a download fails, the next match is tried.
     *
     * @return number of saved images and notices; without a TMDB/IMDb ID nothing is searched
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
        ArtworkSearch search = ScrapeLanguage.with(metadataService.scrapeLanguage(movieId),
                () -> candidateFinder.findAll(MediaType.MOVIE, movie.metadata().title(), movie.metadata().year(),
                        uniqueIds));
        List<LocalizedText> warnings = new ArrayList<>(search.warnings());
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
