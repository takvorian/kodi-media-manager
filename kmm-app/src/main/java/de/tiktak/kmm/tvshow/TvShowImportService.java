package de.tiktak.kmm.tvshow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Takes over TV shows and episodes from a migration into the library. A TV show is recognized by its
 * folder, an episode by file, season and number; a repeated import updates them instead of creating
 * duplicates. NFOs are not written but marked as read, so that the next scan does not overwrite the
 * imported details with the content of the files.
 */
@Service
@RequiredArgsConstructor
public class TvShowImportService {

    /** Maximum number of parameters of an IN query, so that even large libraries work with any database. */
    static final int QUERY_CHUNK_SIZE = 1000;

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final TvShowNfoFiles nfoFiles;

    /**
     * Starts an import. The session preloads the existing TV shows and their episodes so that
     * each episode does not trigger its own queries. It is only valid within the calling transaction.
     *
     * @param showDirectories folders of all TV shows to be imported
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public TvShowImportSession startSession(Collection<String> showDirectories) {
        List<String> hashes = showDirectories.stream().map(PathHash::of).distinct().toList();
        Map<String, TvShow> shows = new HashMap<>();
        for (List<String> chunk : chunks(hashes)) {
            showRepository.findByDirectoryPathHashIn(chunk).forEach(show -> shows.put(show.getDirectoryPathHash(), show));
        }
        Map<EpisodeKey, Episode> episodes = new HashMap<>();
        for (List<Long> chunk : chunks(shows.values().stream().map(TvShow::getId).toList())) {
            episodeRepository.findByTvShowIdIn(chunk).forEach(episode -> episodes.put(EpisodeKey.of(episode), episode));
        }
        return new TvShowImportSession(shows, episodes);
    }

    private static <T> List<List<T>> chunks(List<T> values) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += QUERY_CHUNK_SIZE) {
            chunks.add(new ArrayList<>(values.subList(start, Math.min(start + QUERY_CHUNK_SIZE, values.size()))));
        }
        return chunks;
    }

    /**
     * Import of several TV shows within a running transaction.
     */
    public final class TvShowImportSession {

        private final Map<String, TvShow> showsByHash;
        private final Map<EpisodeKey, Episode> episodesByKey;

        private TvShowImportSession(Map<String, TvShow> showsByHash, Map<EpisodeKey, Episode> episodesByKey) {
            this.showsByHash = showsByHash;
            this.episodesByKey = episodesByKey;
        }

        /**
         * @return the TV show to which its episodes are assigned afterwards, and the result
         */
        public ImportedTvShow importShow(TvShowImport showImport) {
            String hash = PathHash.of(showImport.directoryPath());
            boolean created = !showsByHash.containsKey(hash);
            TvShow show = showsByHash.computeIfAbsent(hash, key -> newShow(showImport));
            show.applyMetadata(showImport.metadata());
            show.applyExtras(showImport.extras());
            if (showImport.language() != null) {
                show.changeMetadataLanguage(showImport.language());
            }
            show.markNfoNotRead();
            Optional<TvNfoState> nfo = nfoFiles.findShowNfo(show.directory());
            nfo.ifPresent(state -> show.markNfoSynchronized(state.modifiedAt()));
            return new ImportedTvShow(show.getId(), new TvImportResult(created, nfo.isPresent()));
        }

        public TvImportResult importEpisode(ImportedTvShow show, EpisodeImport episodeImport) {
            EpisodeKey key = new EpisodeKey(show.id(), episodeImport.filePath(), episodeImport.season(),
                    episodeImport.episode());
            boolean created = !episodesByKey.containsKey(key);
            Episode episode = episodesByKey.computeIfAbsent(key, ignored -> new Episode(show.id(),
                    episodeImport.filePath(), episodeImport.season(), episodeImport.episode(),
                    episodeImport.metadata().title()));
            episode.applyMetadata(episodeImport.metadata());
            episode.applyExtras(keepingKnownStreams(episodeImport.extras(), episode));
            episode.markNfoNotRead();
            if (episodeImport.addedAt() != null) {
                episode.changeAddedAt(episodeImport.addedAt());
            }
            Optional<TvNfoState> nfo = nfoFiles.findEpisodeNfo(episode.file());
            nfo.ifPresent(state -> episode.markNfoSynchronized(state.modifiedAt()));
            if (created) {
                episodeRepository.save(episode);
            }
            return new TvImportResult(created, nfo.isPresent());
        }

        private TvShow newShow(TvShowImport showImport) {
            TvShowMetadata metadata = showImport.metadata();
            return showRepository.save(new TvShow(showImport.sourceId(), showImport.directoryPath(), metadata.title(),
                    metadata.year()));
        }

        private static EpisodeExtras keepingKnownStreams(EpisodeExtras extras, Episode episode) {
            if (!extras.streams().isEmpty()) {
                return extras;
            }
            return extras.withStreams(episode.extras().streams());
        }
    }

    /**
     * A TV show that was taken over.
     *
     * @param id ID of the TV show in KMM
     */
    public record ImportedTvShow(long id, TvImportResult result) {
    }

    private record EpisodeKey(long showId, String filePath, int season, int episode) {

        EpisodeKey {
            Objects.requireNonNull(filePath, "filePath must not be null");
        }

        static EpisodeKey of(Episode episode) {
            return new EpisodeKey(episode.getTvShowId(), episode.getFilePath(), episode.getSeason(),
                    episode.getEpisode());
        }
    }
}
