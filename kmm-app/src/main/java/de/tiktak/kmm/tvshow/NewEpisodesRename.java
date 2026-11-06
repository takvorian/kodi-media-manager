package de.tiktak.kmm.tvshow;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;

/**
 * After a scan, renames the TV shows with new episodes if this is enabled in the settings (like
 * Ember's "Rename when updating the database").
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
