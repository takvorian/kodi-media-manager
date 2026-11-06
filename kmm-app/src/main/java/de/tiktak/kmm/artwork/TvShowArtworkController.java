package de.tiktak.kmm.artwork;

import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.artwork.ArtworkController.ApplyArtworkRequest;
import de.tiktak.kmm.plugin.api.ImageType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Images of TV shows and seasons ({@code ?season=}) as well as stills of episodes.
 */
@RestController
@RequiredArgsConstructor
public class TvShowArtworkController {

    public static final String SHOW_ARTWORK_PATH = "/api/v1/tvshows/{showId}/artwork";
    public static final String SHOW_TYPE_PATH = SHOW_ARTWORK_PATH + "/{type}";
    public static final String SHOW_IMAGE_PATH = SHOW_TYPE_PATH + "/image";
    public static final String SHOW_CANDIDATES_PATH = SHOW_TYPE_PATH + "/candidates";
    public static final String EPISODE_ARTWORK_PATH = "/api/v1/episodes/{episodeId}/artwork";
    public static final String EPISODE_TYPE_PATH = EPISODE_ARTWORK_PATH + "/{type}";
    public static final String EPISODE_IMAGE_PATH = EPISODE_TYPE_PATH + "/image";
    public static final String EPISODE_CANDIDATES_PATH = EPISODE_TYPE_PATH + "/candidates";
    private static final String SEASON = "season";

    private final TvShowArtworkService artworkService;

    @GetMapping(SHOW_ARTWORK_PATH)
    public List<ArtworkInfo> artwork(@PathVariable long showId,
            @RequestParam(name = SEASON, required = false) Integer season) {
        return artworkService.artwork(showId, season);
    }

    @GetMapping(SHOW_IMAGE_PATH)
    public ResponseEntity<Resource> image(@PathVariable long showId, @PathVariable ImageType type,
            @RequestParam(name = SEASON, required = false) Integer season) {
        return file(artworkService.image(showId, type, season));
    }

    @GetMapping(SHOW_CANDIDATES_PATH)
    public ArtworkCandidates candidates(@PathVariable long showId, @PathVariable ImageType type,
            @RequestParam(name = SEASON, required = false) Integer season) {
        return artworkService.candidates(showId, type, season);
    }

    @PutMapping(SHOW_TYPE_PATH)
    public ArtworkInfo apply(@PathVariable long showId, @PathVariable ImageType type,
            @RequestParam(name = SEASON, required = false) Integer season,
            @Valid @RequestBody ApplyArtworkRequest request) {
        return artworkService.apply(showId, type, season, request.url());
    }

    @DeleteMapping(SHOW_TYPE_PATH)
    public ResponseEntity<Void> remove(@PathVariable long showId, @PathVariable ImageType type,
            @RequestParam(name = SEASON, required = false) Integer season) {
        artworkService.remove(showId, type, season);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(EPISODE_ARTWORK_PATH)
    public List<ArtworkInfo> episodeArtwork(@PathVariable long episodeId) {
        return artworkService.episodeArtwork(episodeId);
    }

    @GetMapping(EPISODE_IMAGE_PATH)
    public ResponseEntity<Resource> episodeImage(@PathVariable long episodeId, @PathVariable ImageType type) {
        return file(artworkService.episodeImage(episodeId, type));
    }

    @GetMapping(EPISODE_CANDIDATES_PATH)
    public ArtworkCandidates episodeCandidates(@PathVariable long episodeId, @PathVariable ImageType type) {
        return artworkService.episodeCandidates(episodeId, type);
    }

    @PutMapping(EPISODE_TYPE_PATH)
    public ArtworkInfo applyEpisode(@PathVariable long episodeId, @PathVariable ImageType type,
            @Valid @RequestBody ApplyArtworkRequest request) {
        return artworkService.applyEpisode(episodeId, type, request.url());
    }

    @DeleteMapping(EPISODE_TYPE_PATH)
    public ResponseEntity<Void> removeEpisode(@PathVariable long episodeId, @PathVariable ImageType type) {
        artworkService.removeEpisode(episodeId, type);
        return ResponseEntity.noContent().build();
    }

    /** As with movies: the browser has to revalidate, because images are also replaced outside KMM. */
    private static ResponseEntity<Resource> file(ArtworkFile file) {
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .cacheControl(CacheControl.noCache())
                .lastModified(file.modifiedAt())
                .body(new FileSystemResource(file.file()));
    }
}
