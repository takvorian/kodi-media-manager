package de.tiktak.kmm.movie;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import de.tiktak.kmm.movie.MovieRemoval.RemovedMovie;
import de.tiktak.kmm.removal.RemovalFiles;
import de.tiktak.kmm.scanner.DiscFormat;

@ExtendWith(MockitoExtension.class)
class MovieRemoverTest {

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private MovieRemover remover;

    @Test
    void removesTheMoviesAndTellsKodiAboutEachOfThem() {
        List<Movie> movies = List.of(new Movie(1, "/filme/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1),
                new Movie(1, "/filme/Ronin.mkv", "Ronin", 1998, DiscFormat.FILE, 1));
        when(movieRepository.findAllById(List.of(3L, 4L))).thenReturn(movies);

        remover.remove(new MovieRemoval(List.of(new RemovedMovie(3, "Heat", "/filme/Heat.mkv"),
                new RemovedMovie(4, "Ronin", "/filme/Ronin.mkv")), new RemovalFiles(), List.of()));

        verify(movieRepository).deleteAll(movies);
        verify(events).publishEvent(new MovieRemovedEvent("Heat", "/filme/Heat.mkv"));
        verify(events).publishEvent(new MovieRemovedEvent("Ronin", "/filme/Ronin.mkv"));
    }
}
