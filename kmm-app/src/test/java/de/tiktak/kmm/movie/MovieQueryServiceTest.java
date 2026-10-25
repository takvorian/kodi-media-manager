package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.scanner.DiscFormat;

@ExtendWith(MockitoExtension.class)
class MovieQueryServiceTest {

    private static final Pageable FIRST_PAGE = PageRequest.of(0, 10);
    private static final Movie HEAT = persistedMovie();

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieQueryService service;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void returnsAllMoviesWithoutSearchTerm(String search) {
        when(movieRepository.findAll(FIRST_PAGE)).thenReturn(new PageImpl<>(List.of(HEAT), FIRST_PAGE, 1));

        Page<MovieSummary> page = service.findMovies(search, FIRST_PAGE);

        assertThat(page.getContent()).extracting(MovieSummary::title).containsExactly("Heat");
    }

    @Test
    void searchesByTrimmedTitle() {
        when(movieRepository.findByTitleContainingIgnoreCase("hea", FIRST_PAGE))
                .thenReturn(new PageImpl<>(List.of(HEAT), FIRST_PAGE, 1));

        Page<MovieSummary> page = service.findMovies("  hea ", FIRST_PAGE);

        verify(movieRepository).findByTitleContainingIgnoreCase("hea", FIRST_PAGE);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().getFirst().year()).isEqualTo(1995);
    }

    @Test
    void listsMovieIdsOfSourceSortedByTitle() {
        Movie ronin = new Movie(1L, "/media/Ronin.mkv", "ronin", 1998, DiscFormat.FILE, 1);
        ReflectionTestUtils.setField(ronin, "id", 7L);
        when(movieRepository.findBySourceId(1L)).thenReturn(List.of(ronin, HEAT));

        assertThat(service.findMovieIds(1L)).containsExactly(5L, 7L);
    }

    private static Movie persistedMovie() {
        var movie = new Movie(1L, "/media/Heat.mkv", "Heat", 1995, DiscFormat.FILE, 1);
        ReflectionTestUtils.setField(movie, "id", 5L);
        return movie;
    }
}
