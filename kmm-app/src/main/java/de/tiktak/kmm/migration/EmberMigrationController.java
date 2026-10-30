package de.tiktak.kmm.migration;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
     * Nimmt ein Ember-Profil an und analysiert es, ohne Daten zu übernehmen.
     *
     * @param files Datenbank {@code MyVideosNN.emm} und, soweit vorhanden, die Einstellungsdateien des Profils
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmberInspectionResponse upload(@RequestPart("files") List<MultipartFile> files) {
        return importService.upload(files);
    }

    /**
     * Verwirft das hochgeladene Profil.
     */
    @DeleteMapping("/upload")
    public ResponseEntity<Void> discardUpload() {
        importService.discardUpload();
        return ResponseEntity.noContent().build();
    }

    /**
     * Startet den Import (bzw. Probelauf) des hochgeladenen Profils als Hintergrund-Job.
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

    @ExceptionHandler(EmberProfileUploadException.class)
    ProblemDetail handleInvalidUpload(EmberProfileUploadException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
        problem.setTitle(INVALID_PROFILE_TITLE);
        return problem;
    }
}
