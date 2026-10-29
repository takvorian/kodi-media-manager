package de.tiktak.kmm.theme;

import java.net.URI;
import java.util.List;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.tvshow.TvShowBatchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * Titelmusik einer Serie als {@code theme.mp3} im Ordner.
 */
@RestController
@RequiredArgsConstructor
public class TvShowThemeController {

    public static final String THEME_PATH = "/api/v1/tvshows/{showId}/theme";
    public static final String FILE_PATH = THEME_PATH + "/file";
    public static final String CANDIDATES_PATH = THEME_PATH + "/candidates";
    public static final String FILL_PATH = "/api/v1/tvshows/themes/fill";
    static final int MAX_ENTRIES = 20_000;
    private static final MediaType AUDIO_MPEG = MediaType.parseMediaType("audio/mpeg");

    private final ThemeService themeService;
    private final TvShowBatchService batchService;

    @GetMapping(THEME_PATH)
    public ThemeInfo theme(@PathVariable long showId) {
        return themeService.showTheme(showId);
    }

    /** Die Datei zum Anhören; sie kann auch außerhalb von KMM ersetzt werden. */
    @GetMapping(FILE_PATH)
    public ResponseEntity<Resource> file(@PathVariable long showId) {
        return ResponseEntity.ok()
                .contentType(AUDIO_MPEG)
                .cacheControl(CacheControl.noCache())
                .body(new FileSystemResource(themeService.showThemeFile(showId)));
    }

    @GetMapping(CANDIDATES_PATH)
    public ThemeOptions candidates(@PathVariable long showId) {
        return themeService.showCandidates(showId);
    }

    @PutMapping(THEME_PATH)
    public ThemeInfo apply(@PathVariable long showId, @Valid @RequestBody ThemeChoice choice) {
        return themeService.applyToShow(showId, choice);
    }

    @DeleteMapping(THEME_PATH)
    public ThemeInfo remove(@PathVariable long showId) {
        return themeService.removeFromShow(showId);
    }

    /** Holt für die Auswahl fehlende Titelmusik, nur bei sicherem Treffer, als Job. */
    @PostMapping(FILL_PATH)
    public ResponseEntity<JobSnapshot> fillMissing(@Valid @RequestBody Selection selection) {
        JobSnapshot job = batchService.startFill("Fehlende Titelmusik für %d Serien holen",
                selection.showIds(), themeService::fillShow);
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record Selection(@NotNull @NotEmpty @Size(max = MAX_ENTRIES) List<@NotNull Long> showIds) {
    }
}
