package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.TvShowNfo;
import de.tiktak.kmm.scanner.EpisodeCandidate;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gleicht die Serien einer Quelle mit dem Scan-Ergebnis ab: Neue Serien und Episoden werden angelegt,
 * verschwundene entfernt. Angaben kommen aus den NFOs; bekannte Einträge werden nur aktualisiert,
 * wenn sich ihre NFO seit dem letzten Einlesen geändert hat.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TvShowLibrarySynchronizer {

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final MediaSourceService sourceService;
    private final TvShowNfoFiles nfoFiles;
    private final Clock clock;

    @Transactional
    TvShowScanResult synchronize(long sourceId, List<ScannedTvShow> scannedShows, List<String> unrecognizedFiles) {
        Map<String, TvShow> knownShows = showRepository.findBySourceId(sourceId).stream()
                .collect(Collectors.toMap(TvShow::getDirectoryPath, Function.identity()));
        Map<Long, List<Episode>> knownEpisodes = episodeRepository.findByTvShowIdIn(
                        knownShows.values().stream().map(TvShow::getId).toList()).stream()
                .collect(Collectors.groupingBy(Episode::getTvShowId));
        Outcome outcome = new Outcome();

        for (ScannedTvShow scanned : scannedShows) {
            TvShow show = knownShows.remove(scanned.path());
            if (show == null) {
                show = showRepository.save(new TvShow(sourceId, scanned.path(), scanned.candidate().title(),
                        scanned.candidate().year()));
                outcome.showsAdded++;
            }
            synchronizeShowNfo(show, scanned, outcome);
            synchronizeEpisodes(show, scanned, knownEpisodes.getOrDefault(show.getId(), List.of()), outcome);
        }
        for (TvShow removed : knownShows.values()) {
            outcome.removed += knownEpisodes.getOrDefault(removed.getId(), List.of()).size();
            showRepository.delete(removed);
            outcome.showsRemoved++;
        }
        sourceService.markScanned(sourceId, clock.instant());
        return new TvShowScanResult(sourceId, outcome.showsAdded, outcome.showsRemoved, outcome.added,
                outcome.updated, outcome.removed, outcome.unchanged, outcome.nfoErrors, unrecognizedFiles);
    }

    private void synchronizeShowNfo(TvShow show, ScannedTvShow scanned, Outcome outcome) {
        Optional<TvNfoState> nfo = scanned.showNfo();
        if (nfo.isEmpty() || nfo.get().modifiedAt().equals(show.getNfoModifiedAt())) {
            return;
        }
        try {
            TvShowNfo content = nfoFiles.readShow(nfo.get());
            show.applyMetadata(TvNfoMapper.toMetadata(content, scanned.candidate().title(),
                    scanned.candidate().year()));
            show.applyExtras(TvNfoMapper.toExtras(content));
        } catch (NfoException e) {
            log.warn("Ignoring unreadable NFO: {}", e.getMessage());
            outcome.nfoErrors++;
        }
        show.markNfoSynchronized(nfo.get().modifiedAt());
    }

    private void synchronizeEpisodes(TvShow show, ScannedTvShow scanned, List<Episode> known, Outcome outcome) {
        Map<EpisodeKey, Episode> remaining = new HashMap<>();
        known.forEach(episode -> remaining.put(EpisodeKey.of(episode), episode));
        List<Episode> created = new ArrayList<>();
        for (EpisodeCandidate candidate : scanned.candidate().episodes()) {
            TvNfoState nfo = scanned.episodeNfos().get(candidate.file());
            NfoContent content = new NfoContent(nfo);
            for (int number : candidate.number().episodes()) {
                int season = candidate.number().season();
                Episode episode = remaining.remove(new EpisodeKey(candidate.file().toString(), season, number));
                boolean isNew = episode == null;
                if (isNew) {
                    episode = new Episode(show.getId(), candidate.file().toString(), season, number,
                            defaultTitle(candidate, number));
                    created.add(episode);
                }
                boolean changed = nfo != null && !nfo.modifiedAt().equals(episode.getNfoModifiedAt());
                if (changed) {
                    applyNfo(episode, content, candidate.number().episodes().size() == 1, outcome);
                    episode.markNfoSynchronized(nfo.modifiedAt());
                }
                count(outcome, isNew, changed);
            }
        }
        episodeRepository.saveAll(created);
        episodeRepository.deleteAll(remaining.values());
        outcome.removed += remaining.size();
    }

    private void applyNfo(Episode episode, NfoContent content, boolean singleEpisodeFile, Outcome outcome) {
        Optional<List<EpisodeNfo>> episodes = content.episodes(nfoFiles);
        if (episodes.isEmpty()) {
            if (content.reportFailureOnce()) {
                outcome.nfoErrors++;
            }
            return;
        }
        Optional<EpisodeNfo> matching = episodes.get().stream()
                .filter(nfo -> nfo.describes(episode.getSeason(), episode.getEpisode()))
                .findFirst()
                .or(() -> singleEpisodeFile && episodes.get().size() == 1 ? Optional.of(episodes.get().getFirst())
                        : Optional.empty());
        matching.ifPresent(nfo -> {
            episode.applyMetadata(TvNfoMapper.toMetadata(nfo, episode.getTitle()));
            episode.applyExtras(TvNfoMapper.toExtras(nfo, clock.getZone()));
            Instant dateAdded = TvNfoMapper.toInstant(nfo.dateAdded(), clock.getZone());
            if (dateAdded != null) {
                episode.changeAddedAt(dateAdded);
            }
        });
    }

    private static void count(Outcome outcome, boolean isNew, boolean changed) {
        if (isNew) {
            outcome.added++;
        } else if (changed) {
            outcome.updated++;
        } else {
            outcome.unchanged++;
        }
    }

    /** Titel aus dem Dateinamen, bei Mehrfachepisoden mit der Episodennummer. */
    private static String defaultTitle(EpisodeCandidate candidate, int number) {
        String base = candidate.title() != null ? candidate.title() : "Episode %d".formatted(number);
        String title = candidate.number().episodes().size() > 1 && candidate.title() != null
                ? "%s (%d)".formatted(base, number) : base;
        return TvTexts.clean(title, TvTexts.MAX_TITLE_LENGTH);
    }

    private record EpisodeKey(String filePath, int season, int episode) {

        static EpisodeKey of(Episode episode) {
            return new EpisodeKey(episode.getFilePath(), episode.getSeason(), episode.getEpisode());
        }
    }

    /** Liest die NFO einer Datei höchstens einmal, auch bei Mehrfachepisoden. */
    private static final class NfoContent {

        private final TvNfoState nfo;
        private Optional<List<EpisodeNfo>> episodes;
        private boolean failureReported;

        private NfoContent(TvNfoState nfo) {
            this.nfo = nfo;
        }

        Optional<List<EpisodeNfo>> episodes(TvShowNfoFiles files) {
            if (episodes == null) {
                try {
                    episodes = Optional.of(files.readEpisodes(nfo));
                } catch (NfoException e) {
                    log.warn("Ignoring unreadable NFO: {}", e.getMessage());
                    episodes = Optional.empty();
                }
            }
            return episodes;
        }

        /** Eine unlesbare Mehrfachepisoden-NFO zählt nur einmal als Fehler. */
        boolean reportFailureOnce() {
            boolean first = !failureReported;
            failureReported = true;
            return first;
        }
    }

    private static final class Outcome {
        private int showsAdded;
        private int showsRemoved;
        private int added;
        private int updated;
        private int removed;
        private int unchanged;
        private int nfoErrors;
    }
}
