package de.tiktak.kmm.movie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Takes over movies from a migration into the library. A movie is recognized by its file path;
 * a repeated import updates it instead of creating it twice. The NFO is not
 * written, but marked as read so that the next scan does not overwrite the imported details
 * with the content of the file.
 */
@Service
@RequiredArgsConstructor
public class MovieImportService {

    /** Maximum number of parameters of an IN query, so that even large libraries work with every database. */
    static final int QUERY_CHUNK_SIZE = 1000;

    private final MovieRepository movieRepository;
    private final MovieSets movieSets;
    private final MovieNfoFiles nfoFiles;

    /**
     * Begins an import. The session loads the existing movies in advance and remembers sets so that
     * not every movie triggers its own queries. It is valid only within the calling transaction.
     *
     * @param filePaths file paths of all movies to be imported
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MovieImportSession startSession(Collection<String> filePaths) {
        List<String> hashes = filePaths.stream().map(FilePathHash::of).toList();
        Map<String, Movie> existing = new HashMap<>();
        for (int start = 0; start < hashes.size(); start += QUERY_CHUNK_SIZE) {
            List<String> chunk = new ArrayList<>(hashes.subList(start, Math.min(start + QUERY_CHUNK_SIZE, hashes.size())));
            movieRepository.findByFilePathHashIn(chunk).forEach(movie -> existing.put(movie.getFilePathHash(), movie));
        }
        return new MovieImportSession(existing);
    }

    /**
     * Import of several movies within a running transaction.
     */
    public final class MovieImportSession {

        private final Map<String, Movie> moviesByHash;
        private final Map<String, MovieSet> setsByName = new HashMap<>();

        private MovieImportSession(Map<String, Movie> existingByHash) {
            this.moviesByHash = existingByHash;
        }

        public MovieImportResult importMovie(MovieImport movieImport) {
            String hash = FilePathHash.of(movieImport.filePath());
            boolean created = !moviesByHash.containsKey(hash);
            Movie movie = moviesByHash.computeIfAbsent(hash, key -> newMovie(movieImport));
            movie.applyMetadata(movieImport.metadata());
            MovieSet set = set(movieImport);
            movie.applySupplement(movieImport.supplement(), set);
            movie.changeTrailer(movieImport.trailer());
            if (movieImport.language() != null) {
                movie.changeMetadataLanguage(movieImport.language());
            }
            movie.markNfoNotRead();
            movie.assignSet(set, movieImport.setOrder());
            if (!movieImport.streams().isEmpty()) {
                movie.applyStreamDetails(movieImport.streams());
            }
            if (movieImport.addedAt() != null) {
                movie.changeAddedAt(movieImport.addedAt());
            }
            Optional<NfoFileState> nfo = nfoFiles.find(movie.companionFiles());
            nfo.ifPresent(state -> movie.markNfoSynchronized(state.modifiedAt()));
            if (created) {
                movieRepository.save(movie);
            }
            return new MovieImportResult(created, nfo.isPresent());
        }

        private Movie newMovie(MovieImport movieImport) {
            MovieMetadata metadata = movieImport.metadata();
            return new Movie(movieImport.sourceId(), movieImport.filePath(), metadata.title(), metadata.year(),
                    movieImport.discFormat(), movieImport.partCount());
        }

        private MovieSet set(MovieImport movieImport) {
            MovieSupplement.SetReference reference = movieImport.supplement().set();
            if (reference == null) {
                return null;
            }
            MovieSet set = setsByName.computeIfAbsent(reference.name(), name -> movieSets.resolve(reference));
            set.describe(reference.overview());
            set.identify(movieImport.setTmdbCollectionId());
            return set;
        }
    }
}
