package de.tiktak.kmm.movie;

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
public class MovieLanguageController {

    public static final String LANGUAGE_PATH = MovieController.MOVIES_PATH + "/language";

    private final MovieLanguageService languageService;

    /** Changes the language of the movies as a job, also for a single movie. */
    @PostMapping(LANGUAGE_PATH)
    public ResponseEntity<JobSnapshot> change(@Valid @RequestBody MovieLanguageChange change) {
        JobSnapshot job = languageService.start(change);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }
}
