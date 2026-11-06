package de.tiktak.kmm.tvshow;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.EpisodeNfo;
import de.tiktak.kmm.nfo.TvShowNfo;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.EpisodeCandidate;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.source.ScanDetails;
import de.tiktak.kmm.source.ScanEntry;
import lombok.RequiredArgsConstructor;

/**
 * Reconciles the TV shows of a source with the scan result: new TV shows and episodes are created,
 * vanished ones removed. Details come from the NFOs; known entries are only updated
 * if their NFO changed since it was last read.
 */
@Component
@RequiredArgsConstructor
class TvShowLibrarySynchronizer {

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final MediaSourceService sourceService;
    private final TvShowNfoFiles nfoFiles;
    private final TvNfoSync nfoSync;
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
            boolean isNew = show == null;
            if (isNew) {
                show = showRepository.save(new TvShow(sourceId, scanned.path(), scanned.candidate().title(),
                        scanned.candidate().year()));
                outcome.showsAdded++;
            }
            synchronizeShowNfo(show, scanned, outcome);
            show.fillMissingFromName(scanned.candidate().year(), scanned.candidate().imdbId());
            if (isNew) {
                outcome.details.addedShow(showEntry(show, show.getId()));
            }
            synchronizeEpisodes(show, scanned, knownEpisodes.getOrDefault(show.getId(), List.of()), outcome);
        }
        for (TvShow removed : knownShows.values()) {
            List<Episode> episodes = knownEpisodes.getOrDefault(removed.getId(), List.of());
            outcome.removed += episodes.size();
            episodes.forEach(episode -> outcome.details.removed(episodeEntry(removed, episode, null)));
            showRepository.delete(removed);
            outcome.showsRemoved++;
            outcome.details.removedShow(showEntry(removed, null));
        }
        sourceService.markScanned(sourceId, clock.instant());
        return new TvShowScanResult(sourceId, outcome.showsAdded, outcome.showsRemoved, outcome.added,
                outcome.updated, outcome.removed, outcome.unchanged, outcome.nfoErrors, unrecognizedFiles,
                outcome.details.build(), outcome.newWithoutStreams, List.copyOf(outcome.showsWithNewEpisodes));
    }

    /** Returns the modification times of the NFOs read last, so that the scan only reads changed ones. */
    @Transactional(readOnly = true)
    KnownNfos knownNfos(long sourceId) {
        Map<String, Instant> shows = new HashMap<>();
        for (Object[] row : showRepository.findNfoTimestampsBySourceId(sourceId)) {
            if (row[1] != null) {
                shows.put((String) row[0], (Instant) row[1]);
            }
        }
        Map<String, Set<Instant>> episodeFiles = new HashMap<>();
        for (Object[] row : episodeRepository.findNfoTimestampsBySourceId(sourceId)) {
            episodeFiles.computeIfAbsent((String) row[0], file -> new HashSet<>()).add((Instant) row[1]);
        }
        return new KnownNfos(shows, episodeFiles);
    }

    private void synchronizeShowNfo(TvShow show, ScannedTvShow scanned, Outcome outcome) {
        Optional<TvNfoState> nfo = scanned.showNfo();
        if (nfo.isEmpty() || nfo.get().modifiedAt().equals(show.getNfoModifiedAt())) {
            return;
        }
        Optional<TvShowNfo> content = scanned.loadedShowNfo(nfo.get())
                .orElseGet(() -> nfoFiles.loadShow(nfo.get())).content();
        content.ifPresentOrElse(
                read -> TvNfoSync.applyShow(show, read, scanned.candidate().title(), scanned.candidate().year()),
                () -> {
                    outcome.nfoErrors++;
                    outcome.details.unreadableNfo(scanned.path());
                });
        show.markNfoSynchronized(nfo.get().modifiedAt());
    }

    private void synchronizeEpisodes(TvShow show, ScannedTvShow scanned, List<Episode> known, Outcome outcome) {
        Map<EpisodeKey, Episode> remaining = new HashMap<>();
        known.forEach(episode -> remaining.put(EpisodeKey.of(episode), episode));
        List<Episode> created = new ArrayList<>();
        for (EpisodeCandidate candidate : scanned.candidate().episodes()) {
            TvNfoState nfo = scanned.episodeNfos().get(candidate.file());
            NfoContent content = new NfoContent(nfo, scanned.loadedEpisodeNfo(candidate.file(), nfo));
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
                if (!isNew && changed) {
                    outcome.details.updated(episodeEntry(show, episode, episode.getId()));
                }
            }
        }
        episodeRepository.saveAll(created);
        created.forEach(episode -> outcome.details.added(episodeEntry(show, episode, episode.getId())));
        if (!created.isEmpty()) {
            outcome.showsWithNewEpisodes.add(show.getId());
        }
        created.stream().filter(episode -> !episode.hasStreamDetails())
                .forEach(episode -> outcome.newWithoutStreams.add(episode.getId()));
        episodeRepository.deleteAll(remaining.values());
        remaining.values().forEach(episode -> outcome.details.removed(episodeEntry(show, episode, null)));
        outcome.removed += remaining.size();
    }

    private void applyNfo(Episode episode, NfoContent content, boolean singleEpisodeFile, Outcome outcome) {
        Optional<List<EpisodeNfo>> episodes = content.episodes(nfoFiles);
        if (episodes.isEmpty()) {
            if (content.reportFailureOnce()) {
                outcome.nfoErrors++;
                outcome.details.unreadableNfo(episode.getFilePath());
            }
            return;
        }
        TvNfoSync.matching(episode, episodes.get(), singleEpisodeFile)
                .ifPresent(nfo -> nfoSync.applyEpisode(episode, nfo));
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

    private static ScanEntry showEntry(TvShow show, Long id) {
        return new ScanEntry(MediaType.TV_SHOW, id, show.getTitle());
    }

    private static ScanEntry episodeEntry(TvShow show, Episode episode, Long id) {
        return new ScanEntry(MediaType.TV_EPISODE, id, "%s – S%02dE%02d – %s".formatted(show.getTitle(),
                episode.getSeason(), episode.getEpisode(), episode.getTitle()));
    }

    /** Title from the file name, with the episode number for multi-episode files. */
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

    /**
     * Content of the NFO of a file, read at most once even for multi-episode files. Normally
     * the scan has already read it; it is only read here if it changed since.
     */
    private static final class NfoContent {

        private final TvNfoState nfo;
        private final Optional<LoadedNfo<List<EpisodeNfo>>> loaded;
        private Optional<List<EpisodeNfo>> episodes;
        private boolean failureReported;

        private NfoContent(TvNfoState nfo, Optional<LoadedNfo<List<EpisodeNfo>>> loaded) {
            this.nfo = nfo;
            this.loaded = loaded;
        }

        Optional<List<EpisodeNfo>> episodes(TvShowNfoFiles files) {
            if (episodes == null) {
                episodes = loaded.orElseGet(() -> files.loadEpisodes(nfo)).content();
            }
            return episodes;
        }

        /** An unreadable multi-episode NFO counts as an error only once. */
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
        private final List<Long> newWithoutStreams = new ArrayList<>();
        private final Set<Long> showsWithNewEpisodes = new LinkedHashSet<>();
        private final ScanDetails.Builder details = new ScanDetails.Builder();
    }
}
