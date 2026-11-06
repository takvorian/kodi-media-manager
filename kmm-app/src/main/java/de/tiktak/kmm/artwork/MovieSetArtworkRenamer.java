package de.tiktak.kmm.artwork;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import de.tiktak.kmm.movie.MovieSetChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Takes along the images of a renamed movie set as soon as the new title is saved. An error while
 * moving does not undo the renaming.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieSetArtworkRenamer {

    private final MovieSetArtworkService artworkService;

    @TransactionalEventListener(fallbackExecution = true)
    void onMovieSetChanged(MovieSetChangedEvent event) {
        try {
            artworkService.moveToNewTitle(event.setId(), event.previousName());
        } catch (RuntimeException e) {
            log.warn("Images of movie set {} could not be moved to its new title", event.setId(), e);
        }
    }
}
