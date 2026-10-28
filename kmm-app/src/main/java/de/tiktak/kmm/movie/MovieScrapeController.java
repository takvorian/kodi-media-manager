package de.tiktak.kmm.movie;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.plugin.api.CollectionReference;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Online-Suche nach Metadaten für einen Film. Suche und Vorschlag ändern nichts; übernommen wird über
 * {@code PUT /api/v1/movies/{id}/metadata}, danach optional die Set-Zuordnung.
 */
@RestController
@RequiredArgsConstructor
public class MovieScrapeController {

    public static final String SEARCH_PATH = MovieController.MOVIE_PATH + "/scraper-search";
    public static final String PROPOSAL_PATH = MovieController.MOVIE_PATH + "/scraper-proposal";
    public static final String COLLECTION_PATH = MovieController.MOVIE_PATH + "/collection";

    private final MovieScrapeService scrapeService;

    @GetMapping(SEARCH_PATH)
    public List<ScrapeCandidate> search(
            @PathVariable long movieId,
            @RequestParam String scraper,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Integer year) {
        return scrapeService.search(movieId, scraper, title, year);
    }

    @GetMapping(PROPOSAL_PATH)
    public MovieScrapeProposal proposal(
            @PathVariable long movieId,
            @RequestParam String scraper,
            @RequestParam String providerId) {
        return scrapeService.proposal(movieId, scraper, providerId);
    }

    /** Ordnet den Film nach dem Speichern eines Vorschlags dem Set seiner Sammlung zu, wenn eingestellt. */
    @PostMapping(COLLECTION_PATH)
    public CollectionAssignment assignCollection(@PathVariable long movieId,
            @Valid @RequestBody CollectionRequest collection) {
        return new CollectionAssignment(scrapeService.assignCollection(movieId,
                new CollectionReference(collection.providerId(), collection.name())));
    }

    public record CollectionRequest(@NotBlank @Pattern(regexp = "[0-9]{1,10}") String providerId,
                                    @NotBlank @Size(max = MovieSupplement.MAX_NAME_LENGTH) String name) {
    }

    /**
     * @param assigned ob der Film jetzt zu einem anderen Set gehört
     */
    public record CollectionAssignment(boolean assigned) {
    }
}
