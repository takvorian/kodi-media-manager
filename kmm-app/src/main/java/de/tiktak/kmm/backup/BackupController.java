package de.tiktak.kmm.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.export.TemporaryFileResource;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(BackupController.BASE_PATH)
@RequiredArgsConstructor
public class BackupController {

    public static final String BASE_PATH = "/api/v1/backup";
    public static final String EXPORT_PATH = BASE_PATH + "/export";

    private static final MediaType GZIP = MediaType.parseMediaType("application/gzip");

    private final BackupService backupService;
    private final Clock clock;

    @PostMapping("/export")
    public ResponseEntity<Resource> export(@RequestParam(defaultValue = "false") boolean credentials)
            throws IOException {
        Path file = backupService.export(credentials);
        String fileName = "kmm-sicherung-%s.json.gz".formatted(LocalDate.now(clock));
        return ResponseEntity.ok()
                .contentType(GZIP)
                .contentLength(Files.size(file))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build()
                        .toString())
                .body(new TemporaryFileResource(file));
    }
}
