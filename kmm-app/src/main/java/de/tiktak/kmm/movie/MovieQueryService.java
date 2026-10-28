package de.tiktak.kmm.movie;

import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import de.tiktak.kmm.persistence.StreamIndex;

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
    private final MovieSetRepository setRepository;
    private final Clock clock;

    /**
     * @param search Teil des Titels; leer oder {@code null} liefert alle Filme
     */
    public Page<MovieSummary> findMovies(String search, Pageable pageable) {
        Page<Movie> movies = search == null || search.isBlank()
                ? movieRepository.findAll(pageable)
                : movieRepository.findByTitleContainingIgnoreCase(search.strip(), pageable);
        return movies.map(MovieSummary::from);
    }

    /**
     * @throws InvalidFilterException wenn eine Bedingung nicht zu ihrem Feld passt
     */
    public Page<MovieSummary> findFiltered(MovieFilter filter, Pageable pageable) {
        return movieRepository.findAll(MovieFilterSpecification.of(filter, clock), pageable).map(MovieSummary::from);
    }

    /** IDs aller Filme, auf die der Filter passt, etwa für die Stapel-Bearbeitung. */
    public List<Long> findMovieIds(MovieFilter filter) {
        return movieRepository.findAll(MovieFilterSpecification.of(filter, clock)).stream().map(Movie::getId).toList();
    }

    public MovieFilterValues filterValues() {
        return new MovieFilterValues(
                MovieFilterValues.sorted(movieRepository.findDistinctGenres()),
                MovieFilterValues.sorted(movieRepository.findDistinctTags()),
                MovieFilterValues.sorted(movieRepository.findDistinctCountries()),
                MovieFilterValues.sorted(movieRepository.findDistinctStudios()),
                MovieFilterValues.sorted(setRepository.findAll().stream().map(MovieSet::getName).toList()),
                MovieFilterValues.sorted(movieRepository.findDistinctMpaa()),
                MovieFilterValues.sorted(movieRepository.findDistinctEditions()),
                MovieFilterValues.sorted(movieRepository.findDistinctVideoSources()),
                movieRepository.findDistinctResolutions().stream().sorted().toList(),
                MovieFilterValues.sorted(movieRepository.findDistinctVideoCodecs()),
                MovieFilterValues.sorted(movieRepository.findDistinctHdrTypes()),
                MovieFilterValues.sorted(tokens(movieRepository.findDistinctAudioCodecLists())),
                MovieFilterValues.sorted(tokens(movieRepository.findDistinctAudioLanguageLists())),
                MovieFilterValues.sorted(tokens(movieRepository.findDistinctSubtitleLanguageLists())));
    }

    private static List<String> tokens(Collection<String> lists) {
        return lists.stream().flatMap(list -> StreamIndex.tokensOf(list).stream()).toList();
    }

    /** IDs der Filme einer Quelle, nach Titel sortiert. */
    public List<Long> findMovieIds(long sourceId) {
        return movieRepository.findBySourceId(sourceId).stream()
                .sorted(Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(Movie::getId)
                .toList();
    }
}
