package de.tiktak.kmm.mediainfo;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import de.tiktak.kmm.movie.MovieSourceScannedEvent;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.tvshow.TvShowSourceScannedEvent;
import lombok.RequiredArgsConstructor;

/**
 * After a scan, reads the newly found files whose NFO contains no technical details.
 */
@Component
@RequiredArgsConstructor
class NewFilesStreamDetails {

    private final StreamDetailsService service;

    @TransactionalEventListener(fallbackExecution = true)
    void onMovieSourceScanned(MovieSourceScannedEvent event) {
        service.startNewFiles(event.sourceId(), MediaType.MOVIE, event.newWithoutStreams());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onTvSourceScanned(TvShowSourceScannedEvent event) {
        service.startNewFiles(event.sourceId(), MediaType.TV_SHOW, event.newWithoutStreams());
    }
}
