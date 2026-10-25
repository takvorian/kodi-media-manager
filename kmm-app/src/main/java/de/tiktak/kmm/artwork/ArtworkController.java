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
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.plugin.api.ImageType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ArtworkController {

    public static final String ARTWORK_PATH = "/api/v1/movies/{movieId}/artwork";
    public static final String TYPE_PATH = ARTWORK_PATH + "/{type}";
    public static final String IMAGE_PATH = TYPE_PATH + "/image";
    public static final String CANDIDATES_PATH = TYPE_PATH + "/candidates";

    private final ArtworkService artworkService;

    @GetMapping(ARTWORK_PATH)
    public List<ArtworkInfo> artwork(@PathVariable long movieId) {
        return artworkService.artwork(movieId);
    }

    /**
     * Liefert die Bilddatei. Die Oberfläche hängt die Änderungszeit an die URL; der Browser muss
     * trotzdem nachfragen, weil Bilder auch außerhalb von KMM ersetzt werden können.
     */
    @GetMapping(IMAGE_PATH)
    public ResponseEntity<Resource> image(@PathVariable long movieId, @PathVariable ImageType type) {
        ArtworkFile file = artworkService.image(movieId, type);
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .cacheControl(CacheControl.noCache())
                .lastModified(file.modifiedAt())
                .body(new FileSystemResource(file.file()));
    }

    @GetMapping(CANDIDATES_PATH)
    public ArtworkCandidates candidates(@PathVariable long movieId, @PathVariable ImageType type) {
        return artworkService.candidates(movieId, type);
    }

    @PutMapping(TYPE_PATH)
    public ArtworkInfo apply(@PathVariable long movieId, @PathVariable ImageType type,
            @Valid @RequestBody ApplyArtworkRequest request) {
        return artworkService.apply(movieId, type, request.url());
    }

    @DeleteMapping(TYPE_PATH)
    public ResponseEntity<Void> remove(@PathVariable long movieId, @PathVariable ImageType type) {
        artworkService.remove(movieId, type);
        return ResponseEntity.noContent().build();
    }

    /**
     * @param url Adresse des Bildes in voller Auflösung, meist aus einem {@link ArtworkCandidate}
     */
    public record ApplyArtworkRequest(@NotBlank String url) {
    }
}
