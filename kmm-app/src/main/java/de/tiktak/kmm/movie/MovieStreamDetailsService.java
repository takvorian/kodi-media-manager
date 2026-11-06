package de.tiktak.kmm.movie;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.media.StreamDetailsMerge;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.StreamDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Takes over newly read stream details of a movie into the existing NFO and the database;
 * without an NFO, only into the database. Languages and 3D details that ffprobe does not know are kept.
 * As with the playback state, no {@link MovieFilesChangedEvent} is published: Kodi reads the
 * details itself during playback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MovieStreamDetailsService {

    private final MovieRepository movieRepository;
    private final MovieNfoFiles nfoFiles;

    public List<MovieMedia> findBySource(long sourceId) {
        return movieRepository.findBySourceId(sourceId).stream().map(MovieMedia::of).toList();
    }

    public MovieMedia find(long movieId) {
        return MovieMedia.of(require(movieId));
    }

    /**
     * @return the details that were taken over
     * @throws NfoNotWritableException if the NFO cannot be written; the database then
     *                                 remains unchanged
     */
    @Transactional
    public StreamDetails changeStreamDetails(long movieId, StreamDetails probed) {
        Movie movie = require(movieId);
        StreamDetails merged = StreamDetailsMerge.keepKnown(probed, movie.streamDetails());
        nfoFiles.find(movie.companionFiles()).ifPresent(nfoFile -> {
            try {
                NfoFileState written = nfoFiles.writeStreamDetails(nfoFile, merged);
                if (nfoFile.modifiedAt().equals(movie.getNfoModifiedAt())) {
                    movie.markNfoSynchronized(written.modifiedAt());
                }
            } catch (NfoException e) {
                throw new NfoNotWritableException(e);
            }
        });
        movie.applyStreamDetails(merged);
        log.info("Stream details of movie {} changed", movieId);
        return merged;
    }

    private Movie require(long movieId) {
        return movieRepository.findById(movieId).orElseThrow(() -> new MovieNotFoundException(movieId));
    }
}
