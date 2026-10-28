package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import de.tiktak.kmm.movie.MovieSetChangedEvent;

class MovieSetArtworkRenamerTest {

    private final MovieSetArtworkService artworkService = mock(MovieSetArtworkService.class);
    private final MovieSetArtworkRenamer renamer = new MovieSetArtworkRenamer(artworkService);

    @Test
    void movesTheImagesToTheNewTitle() {
        renamer.onMovieSetChanged(new MovieSetChangedEvent(7L, "Matrix"));

        verify(artworkService).moveToNewTitle(7L, "Matrix");
    }

    @Test
    void keepsTheRenameWhenTheImagesCannotBeMoved() {
        doThrow(new IllegalStateException("gesperrt")).when(artworkService).moveToNewTitle(7L, "Matrix");

        assertThatCode(() -> renamer.onMovieSetChanged(new MovieSetChangedEvent(7L, "Matrix")))
                .doesNotThrowAnyException();
    }
}
