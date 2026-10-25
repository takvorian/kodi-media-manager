package de.tiktak.kmm.autoscrape;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AutoScrapeController {

    public static final String AUTO_SCRAPE_PATH = "/api/v1/sources/{sourceId}/auto-scrape";

    private final AutoScrapeService autoScrapeService;

    /**
     * Startet das automatische Scrapen im Hintergrund. Fortschritt und Ergebnis liefert die Job-API.
     */
    @PostMapping(AUTO_SCRAPE_PATH)
    public ResponseEntity<JobSnapshot> start(@PathVariable long sourceId) {
        JobSnapshot job = autoScrapeService.start(sourceId);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }
}
