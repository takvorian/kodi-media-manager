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
 * Übernimmt neu ausgelesene technische Angaben eines Films in die vorhandene NFO und die Datenbank;
 * ohne NFO nur in die Datenbank. Sprachen und 3D-Angaben, die ffprobe nicht kennt, bleiben erhalten.
 * Wie beim Wiedergabestand wird kein {@link MovieFilesChangedEvent} veröffentlicht: Kodi liest die
 * Angaben beim Abspielen selbst aus.
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
     * @return die übernommenen Angaben
     * @throws NfoNotWritableException wenn die NFO nicht geschrieben werden kann; die Datenbank bleibt
     *                                 dann unverändert
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
