package de.tiktak.kmm.movie;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.movie.MovieFileMover.Relocation;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.StreamDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Renames a movie according to its plan: first in the file system, then in the database. External subtitles
 * are stored with their path in the technical details; they are updated in the database and NFO.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieRenamer {

    private final MovieRepository movieRepository;
    private final MovieNfoFiles nfoFiles;
    private final ApplicationEventPublisher events;

    /**
     * @param plan a plan with status {@link MovieRenamePlan.Status#RENAME}
     * @throws IOException if the file system rejects the change; nothing is changed then
     */
    @Transactional
    public void rename(MovieRenamePlan plan, Path sourceRoot) throws IOException {
        Movie movie = movieRepository.findById(plan.movieId())
                .orElseThrow(() -> new MovieNotFoundException(plan.movieId()));
        Relocation relocation = MovieFileMover.move(plan, sourceRoot);
        movie.relocate(relocation.relocate(Path.of(movie.getFilePath())).toString());
        relocateSubtitles(movie, relocation);
        events.publishEvent(new MovieRenamedEvent(movie.getId(), movie.getSourceId(), plan.currentDirectory(),
                plan.targetDirectory()));
        log.info("Movie {} renamed: {} -> {}, {} file(s)", movie.getId(), plan.currentDirectory(),
                plan.targetDirectory(), plan.renames().size());
    }

    private void relocateSubtitles(Movie movie, Relocation relocation) {
        StreamDetails streams = movie.streamDetails();
        StreamDetails relocated = new StreamDetails(streams.videos(), streams.audios(), streams.subtitles().stream()
                .map(subtitle -> subtitle.path() == null ? subtitle : new StreamDetails.Subtitle(subtitle.language(),
                        subtitle.forced(), relocation.relocate(Path.of(subtitle.path())).toString()))
                .toList());
        if (relocated.equals(streams)) {
            return;
        }
        movie.applyStreamDetails(relocated);
        nfoFiles.find(movie.companionFiles()).ifPresent(nfoFile -> {
            try {
                NfoFileState written = nfoFiles.writeStreamDetails(nfoFile, relocated);
                if (nfoFile.modifiedAt().equals(movie.getNfoModifiedAt())) {
                    movie.markNfoSynchronized(written.modifiedAt());
                }
            } catch (NfoException e) {
                log.warn("Subtitle paths not updated in {}: {}", nfoFile.file(), e.getMessage());
            }
        });
    }
}
