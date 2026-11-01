package de.tiktak.kmm.tvshow;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class TvShowLanguageController {

    public static final String LANGUAGE_PATH = "/api/v1/tvshows/language";

    private final TvShowLanguageService languageService;

    /** Ändert die Sprache der Serien als Job, auch für eine einzelne Serie. */
    @PostMapping(LANGUAGE_PATH)
    public ResponseEntity<JobSnapshot> change(@Valid @RequestBody TvShowLanguageChange change) {
        JobSnapshot job = languageService.start(change);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }
}
