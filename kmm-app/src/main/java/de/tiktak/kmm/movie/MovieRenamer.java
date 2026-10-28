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
 * Benennt einen Film nach seinem Plan um: zuerst im Dateisystem, dann in der Datenbank. Externe Untertitel
 * stehen mit Pfad in den technischen Angaben; sie werden in Datenbank und NFO nachgezogen.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieRenamer {

    private final MovieRepository movieRepository;
    private final MovieNfoFiles nfoFiles;
    private final ApplicationEventPublisher events;

    /**
     * @param plan ein Plan mit Status {@link MovieRenamePlan.Status#RENAME}
     * @throws IOException wenn das Dateisystem die Änderung ablehnt; dann ist nichts geändert
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
