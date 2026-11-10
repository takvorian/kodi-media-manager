package de.tiktak.kmm.movie;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes movies from the database and tells Kodi about it after the commit. Credits, ratings, genres, tags and the
 * other details go with them; a movie set stays, even if it has no movies left.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieRemover {

    private final MovieRepository movieRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void remove(MovieRemoval removal) {
        movieRepository.deleteAll(movieRepository.findAllById(removal.movieIds()));
        removal.movies().forEach(movie -> events.publishEvent(new MovieRemovedEvent(movie.title(), movie.filePath())));
        log.info("{} movie(s) removed from the library: {}", removal.movies().size(), removal.movieIds());
    }
}
