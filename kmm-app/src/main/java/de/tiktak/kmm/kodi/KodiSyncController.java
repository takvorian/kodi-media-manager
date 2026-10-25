package de.tiktak.kmm.kodi;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Manueller Abgleich mit allen Kodi-Geräten, unabhängig vom Echtzeit-Sync.
 */
@RestController
@RequestMapping(KodiSyncController.BASE_PATH)
@RequiredArgsConstructor
public class KodiSyncController {

    public static final String BASE_PATH = "/api/v1/kodi";

    private final KodiSyncService syncService;

    @PostMapping("/movies/{movieId}/refresh")
    public List<KodiSyncResult> refreshMovie(@PathVariable long movieId) {
        return syncService.refreshMovie(movieId);
    }

    @PostMapping("/sources/{sourceId}/scan")
    public List<KodiSyncResult> scanSource(@PathVariable long sourceId) {
        return syncService.scanSource(sourceId);
    }
}
