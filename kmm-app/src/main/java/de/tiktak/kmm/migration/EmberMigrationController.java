package de.tiktak.kmm.migration;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(EmberMigrationController.BASE_PATH)
@RequiredArgsConstructor
@Validated
public class EmberMigrationController {

    public static final String BASE_PATH = "/api/v1/migrations/ember";
    static final String INVALID_PROFILE_TITLE = "Ungültiges Ember-Profil";

    private final EmberImportService importService;

    /**
     * Analysiert ein Ember-Profil, ohne Daten zu übernehmen.
     *
     * @param path Ember-Profilverzeichnis oder Ember-Installationsverzeichnis
     */
    @GetMapping("/inspect")
    public EmberInspectionResponse inspect(@RequestParam String path) {
        return importService.inspect(Path.of(path));
    }

    /**
     * Startet den Import (bzw. Probelauf) als Hintergrund-Job.
     */
    @PostMapping("/import")
    public ResponseEntity<JobSnapshot> startImport(@Valid @RequestBody EmberImportRequest request) {
        JobSnapshot job = importService.start(request);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    @ExceptionHandler(EmberProfileException.class)
    ProblemDetail handleInvalidProfile(EmberProfileException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle(INVALID_PROFILE_TITLE);
        return problem;
    }

    @ExceptionHandler(InvalidPathException.class)
    ProblemDetail handleInvalidPath(InvalidPathException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "'%s' ist kein gültiger Pfad.".formatted(exception.getInput()));
        problem.setTitle(INVALID_PROFILE_TITLE);
        return problem;
    }
}
