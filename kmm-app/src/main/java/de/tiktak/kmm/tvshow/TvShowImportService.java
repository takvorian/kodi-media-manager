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
 * Übernimmt Serien und Episoden aus einer Migration in die Bibliothek. Eine Serie wird über ihren
 * Ordner erkannt, eine Episode über Datei, Staffel und Nummer; ein erneuter Import aktualisiert sie,
 * statt sie doppelt anzulegen. NFOs werden nicht geschrieben, aber als eingelesen vermerkt, damit der
 * nächste Scan die importierten Angaben nicht mit dem Inhalt der Dateien überschreibt.
 */
@Service
@RequiredArgsConstructor
public class TvShowImportService {

    /** Höchstzahl der Parameter einer IN-Abfrage, damit auch große Bibliotheken jede Datenbank vertragen. */
    static final int QUERY_CHUNK_SIZE = 1000;

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final TvShowNfoFiles nfoFiles;

    /**
     * Beginnt einen Import. Die Sitzung lädt die vorhandenen Serien und ihre Episoden vorab, damit
     * nicht jede Episode eigene Abfragen auslöst. Sie ist nur innerhalb der aufrufenden Transaktion gültig.
     *
     * @param showDirectories Ordner aller Serien, die importiert werden sollen
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
     * Import mehrerer Serien innerhalb einer laufenden Transaktion.
     */
    public final class TvShowImportSession {

        private final Map<String, TvShow> showsByHash;
        private final Map<EpisodeKey, Episode> episodesByKey;

        private TvShowImportSession(Map<String, TvShow> showsByHash, Map<EpisodeKey, Episode> episodesByKey) {
            this.showsByHash = showsByHash;
            this.episodesByKey = episodesByKey;
        }

        /**
         * @return die Serie, der anschließend ihre Episoden zugeordnet werden, und das Ergebnis
         */
        public ImportedTvShow importShow(TvShowImport showImport) {
            String hash = PathHash.of(showImport.directoryPath());
            boolean created = !showsByHash.containsKey(hash);
            TvShow show = showsByHash.computeIfAbsent(hash, key -> newShow(showImport));
            show.applyMetadata(showImport.metadata());
            show.applyExtras(showImport.extras());
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
     * Eine übernommene Serie.
     *
     * @param id ID der Serie in KMM
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
