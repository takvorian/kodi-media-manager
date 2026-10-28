package de.tiktak.kmm.tvshow;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

/**
 * Benennt nach einem Scan die Serien mit neuen Episoden um, wenn das in den Einstellungen eingeschaltet ist (wie
 * Embers „Beim Aktualisieren der Datenbank umbenennen“).
 */
@Component
@RequiredArgsConstructor
class NewEpisodesRename {

    private final TvShowRenameService renameService;

    @TransactionalEventListener(fallbackExecution = true)
    void onScanned(TvShowSourceScannedEvent event) {
        event.showsWithNewEpisodes().forEach(showId -> renameService.renameIfEnabled(showId, TvRenameTrigger.SCAN));
    }
}
