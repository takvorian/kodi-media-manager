package de.tiktak.kmm.artwork;

import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.artwork.ArtworkController.ApplyArtworkRequest;
import de.tiktak.kmm.plugin.api.ImageType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MovieSetArtworkController {

    public static final String ARTWORK_PATH = "/api/v1/moviesets/{setId}/artwork";
    public static final String FILL_PATH = ARTWORK_PATH + "/fill";
    public static final String TYPE_PATH = ARTWORK_PATH + "/{type}";
    public static final String IMAGE_PATH = TYPE_PATH + "/image";
    public static final String CANDIDATES_PATH = TYPE_PATH + "/candidates";

    private final MovieSetArtworkService artworkService;

    @GetMapping(ARTWORK_PATH)
    public List<ArtworkInfo> artwork(@PathVariable long setId) {
        return artworkService.artwork(setId);
    }

    @GetMapping(IMAGE_PATH)
    public ResponseEntity<Resource> image(@PathVariable long setId, @PathVariable ImageType type) {
        ArtworkFile file = artworkService.image(setId, type);
        return ResponseEntity.ok()
                .contentType(file.mediaType())
                .cacheControl(CacheControl.noCache())
                .lastModified(file.modifiedAt())
                .body(new FileSystemResource(file.file()));
    }

    @GetMapping(CANDIDATES_PATH)
    public ArtworkCandidates candidates(@PathVariable long setId, @PathVariable ImageType type) {
        return artworkService.candidates(setId, type);
    }

    @PutMapping(TYPE_PATH)
    public ArtworkInfo apply(@PathVariable long setId, @PathVariable ImageType type,
            @Valid @RequestBody ApplyArtworkRequest request) {
        return artworkService.apply(setId, type, request.url());
    }

    /** Fetches missing images of the set; existing ones stay. */
    @PostMapping(FILL_PATH)
    public ArtworkFillResult fill(@PathVariable long setId) {
        return artworkService.fillMissing(setId);
    }

    @DeleteMapping(TYPE_PATH)
    public ResponseEntity<Void> remove(@PathVariable long setId, @PathVariable ImageType type) {
        artworkService.remove(setId, type);
        return ResponseEntity.noContent().build();
    }
}
