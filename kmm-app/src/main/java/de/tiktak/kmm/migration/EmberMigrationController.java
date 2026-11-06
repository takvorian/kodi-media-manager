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

import de.tiktak.kmm.i18n.LocalizedException;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import de.tiktak.kmm.web.LocalizedProblems;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(EmberMigrationController.BASE_PATH)
@RequiredArgsConstructor
@Validated
public class EmberMigrationController {

    public static final String BASE_PATH = "/api/v1/migrations/ember";
    static final LocalizedText INVALID_PROFILE_TITLE = LocalizedText.of("migration.invalidProfile.title");

    private final EmberImportService importService;

    /**
     * Accepts an Ember profile and analyzes it without importing any data.
     *
     * @param files database {@code MyVideosNN.emm} and, if present, the settings files of the profile
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EmberInspectionResponse upload(@RequestPart("files") List<MultipartFile> files) {
        return importService.upload(files);
    }

    /**
     * Discards the uploaded profile.
     */
    @DeleteMapping("/upload")
    public ResponseEntity<Void> discardUpload() {
        importService.discardUpload();
        return ResponseEntity.noContent().build();
    }

    /**
     * Starts the import (or dry run) of the uploaded profile as background job.
     */
    @PostMapping("/import")
    public ResponseEntity<JobSnapshot> startImport(@Valid @RequestBody EmberImportRequest request) {
        JobSnapshot job = importService.start(request);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    @ExceptionHandler({EmberProfileException.class, EmberProfileUploadException.class})
    ProblemDetail handleInvalidProfile(LocalizedException exception) {
        return LocalizedProblems.problem(HttpStatus.UNPROCESSABLE_CONTENT, INVALID_PROFILE_TITLE,
                exception.localizedText());
    }
}
