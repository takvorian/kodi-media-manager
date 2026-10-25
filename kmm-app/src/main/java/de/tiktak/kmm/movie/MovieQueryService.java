package de.tiktak.kmm.movie;

import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieQueryService {

    private final MovieRepository movieRepository;

    /**
     * @param search Teil des Titels; leer oder {@code null} liefert alle Filme
     */
    public Page<MovieSummary> findMovies(String search, Pageable pageable) {
        Page<Movie> movies = search == null || search.isBlank()
                ? movieRepository.findAll(pageable)
                : movieRepository.findByTitleContainingIgnoreCase(search.strip(), pageable);
        return movies.map(MovieSummary::from);
    }

    /** IDs der Filme einer Quelle, nach Titel sortiert. */
    public List<Long> findMovieIds(long sourceId) {
        return movieRepository.findBySourceId(sourceId).stream()
                .sorted(Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(Movie::getId)
                .toList();
    }
}
