package de.tiktak.kmm.mediainfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobAlreadyRunningException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.ParallelReads;
import de.tiktak.kmm.media.MediaProbeException;
import de.tiktak.kmm.movie.MovieMedia;
import de.tiktak.kmm.movie.MovieStreamDetailsService;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.EpisodeMedia;
import de.tiktak.kmm.tvshow.EpisodeStreamDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads technical details with ffprobe and applies them to NFO and database. Episodes of a
 * multi-episode file are read together, because they share the file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamDetailsService {

    static final String JOB_KEY_PREFIX = "stream-details:";
    static final String NEW_FILES_JOB_KEY_PREFIX = "stream-details-new:";
    /** Prefix per TV show; at most one season of the show is read at a time. */
    static final String SEASON_JOB_KEY_PREFIX = "stream-details-season:";
    static final int MAX_LISTED_FAILURES = 20;
    /** Concurrent ffprobe calls; each reads only the start of the file, more would burden the NAS. */
    static final int PARALLEL_PROBES = 4;
    private static final String EPISODE_LABEL = "%s S%02dE%02d";

    private final MediaSourceService sourceService;
    private final MovieStreamDetailsService movies;
    private final EpisodeStreamDetailsService episodes;
    private final MediaProbes probes;
    private final JobService jobService;
    /** IDs per source that the job for new files still has to read; guarded by itself. */
    private final Map<Long, Set<Long>> pendingNewFiles = new HashMap<>();
    /** Sources whose job for new files is running; guarded by {@link #pendingNewFiles}. */
    private final Set<Long> newFilesRunning = new HashSet<>();

    /**
     * @throws MediaInfoProblemException if ffprobe cannot read the file
     */
    public StreamDetails probeMovie(long movieId) {
        MovieMedia media = movies.find(movieId);
        return movies.changeStreamDetails(movieId, probe(media.file(), media.format()));
    }

    /**
     * @throws MediaInfoProblemException if ffprobe cannot read the file
     */
    public StreamDetails probeEpisode(long episodeId) {
        EpisodeMedia media = episodes.find(episodeId);
        return episodes.changeStreamDetails(episodeId, probe(media.file(), DiscFormat.FILE));
    }

    /** Reads the movies or episodes of the source as a background job. */
    public JobSnapshot startSource(long sourceId, StreamDetailsScope scope) {
        MediaSource source = sourceService.require(sourceId);
        LocalizedText title = LocalizedText.of("mediaInfo.source.jobTitle").with("source", source.getName());
        return jobService.submit(JOB_KEY_PREFIX + sourceId, title,
                context -> run(sourceId, entries(source, scope == StreamDetailsScope.ALL, id -> true), context));
    }

    /**
     * Re-reads all episodes of a season as a background job, including those with existing details.
     *
     * @throws MediaInfoProblemException if the season has no episodes
     */
    public JobSnapshot startSeason(long showId, int season) {
        List<EpisodeMedia> media = episodes.findByShow(showId).stream()
                .filter(candidate -> candidate.season() == season)
                .sorted(Comparator.comparingInt(EpisodeMedia::episode))
                .toList();
        if (media.isEmpty()) {
            throw MediaInfoProblemException.seasonWithoutEpisodes(episodes.showTitleOf(showId), season);
        }
        long sourceId = episodes.sourceIdOf(showId);
        List<Entry> entries = perFile(media, EpisodeMedia::file).stream()
                .map(episode -> episodeEntry(episode, true))
                .toList();
        LocalizedText title = LocalizedText.of("mediaInfo.season.jobTitle")
                .with("season", season)
                .with("show", media.getFirst().showTitle());
        return jobService.submit(SEASON_JOB_KEY_PREFIX + showId, title, context -> run(sourceId, entries, context));
    }

    /**
     * Reads newly found files if that is enabled. If such a job is already running for the source, it takes the
     * files over before it ends, so that e.g. a batch scan of many movies loses none.
     */
    void startNewFiles(long sourceId, MediaType mediaType, Collection<Long> ids) {
        if (ids.isEmpty() || !probes.probeNewFiles()) {
            return;
        }
        MediaSource source = sourceService.require(sourceId);
        synchronized (pendingNewFiles) {
            pendingNewFiles.computeIfAbsent(sourceId, key -> new LinkedHashSet<>()).addAll(ids);
            if (!newFilesRunning.add(sourceId)) {
                log.debug("Queued {} new {} file(s) for the running job of source {}", ids.size(), mediaType,
                        sourceId);
                return;
            }
        }
        LocalizedText title = LocalizedText.of("mediaInfo.newFiles.jobTitle").with("source", source.getName());
        try {
            jobService.submit(NEW_FILES_JOB_KEY_PREFIX + sourceId, title, context -> runNewFiles(source, context));
        } catch (JobAlreadyRunningException e) {
            forgetNewFiles(sourceId);
            log.info("Stream details of new files in source {} are already being read", sourceId);
        }
    }

    /** Reads the queued files of the source until no more arrive; a cancelled or failed job drops the rest. */
    private StreamDetailsJobResult runNewFiles(MediaSource source, JobContext context) {
        long sourceId = source.getId();
        StreamDetailsJobResult total = new StreamDetailsJobResult(sourceId, 0, 0, 0, List.of());
        try {
            while (true) {
                Set<Long> wanted;
                synchronized (pendingNewFiles) {
                    wanted = pendingNewFiles.remove(sourceId);
                    if (wanted == null) {
                        newFilesRunning.remove(sourceId);
                        return total;
                    }
                }
                total = combined(total, run(sourceId, entries(source, true, wanted::contains), context));
            }
        } catch (RuntimeException e) {
            forgetNewFiles(sourceId);
            throw e;
        }
    }

    private void forgetNewFiles(long sourceId) {
        synchronized (pendingNewFiles) {
            pendingNewFiles.remove(sourceId);
            newFilesRunning.remove(sourceId);
        }
    }

    private static StreamDetailsJobResult combined(StreamDetailsJobResult first, StreamDetailsJobResult second) {
        List<LocalizedText> failures = new ArrayList<>(first.failures());
        failures.addAll(second.failures());
        return new StreamDetailsJobResult(first.sourceId(), first.updated() + second.updated(),
                first.skipped() + second.skipped(), first.failed() + second.failed(),
                failures.subList(0, Math.min(failures.size(), MAX_LISTED_FAILURES)));
    }

    /**
     * Reads a movie after scraping if that is enabled.
     *
     * @return the applied details; empty if reading after scraping is disabled
     * @throws MediaInfoProblemException if ffprobe cannot read the file
     */
    public Optional<StreamDetails> probeMovieIfEnabled(long movieId) {
        return probes.probeOnScrape() ? Optional.of(probeMovie(movieId)) : Optional.empty();
    }

    /**
     * Reads a movie after automatic scraping if that is enabled.
     *
     * @return an error message if reading failed
     */
    public Optional<LocalizedText> probeMovieAfterScrape(long movieId) {
        return failureOf(() -> probeMovieIfEnabled(movieId));
    }

    /**
     * After scraping a TV show, reads the episodes without technical details if that is
     * enabled. Existing details, e.g. from Ember, stay so that scraping stays fast.
     *
     * @return empty if reading after scraping is disabled
     */
    public Optional<ShowStreamDetailsResult> probeShowIfEnabled(long showId) {
        return probeEpisodesIfEnabled(episodes.findByShow(showId));
    }

    private Optional<ShowStreamDetailsResult> probeEpisodesIfEnabled(List<EpisodeMedia> candidates) {
        if (!probes.probeOnScrape()) {
            return Optional.empty();
        }
        int updated = 0;
        List<LocalizedText> failures = new ArrayList<>();
        for (EpisodeMedia media : perFile(candidates, EpisodeMedia::file)) {
            if (!media.hasStreamDetails()) {
                Optional<LocalizedText> failure = failureOf(() -> probeEpisode(media.episodeId()));
                failure.ifPresent(failures::add);
                updated += failure.isEmpty() ? 1 : 0;
            }
        }
        return Optional.of(new ShowStreamDetailsResult(updated, failures));
    }

    /**
     * After automatic scraping of a TV show, reads the episodes without technical details.
     *
     * @return the error messages of the files that could not be read
     * @see #probeShowIfEnabled(long)
     */
    public List<LocalizedText> probeEpisodesAfterScrape(long showId) {
        return probeShowIfEnabled(showId).map(ShowStreamDetailsResult::failures).orElse(List.of());
    }

    /**
     * Like {@link #probeEpisodesAfterScrape(long)}, but only for the episodes of one season.
     *
     * @return the error messages of the files that could not be read
     */
    public List<LocalizedText> probeSeasonAfterScrape(long showId, int season) {
        return probeEpisodesIfEnabled(episodes.findByShow(showId).stream()
                .filter(media -> media.season() == season)
                .toList())
                .map(ShowStreamDetailsResult::failures).orElse(List.of());
    }

    /**
     * Like {@link #probeEpisodesAfterScrape(long)}, but only for the file of one episode.
     *
     * @return the error message if the file could not be read
     */
    public List<LocalizedText> probeEpisodeAfterScrape(long episodeId) {
        return probeEpisodesIfEnabled(List.of(episodes.find(episodeId)))
                .map(ShowStreamDetailsResult::failures).orElse(List.of());
    }

    StreamDetailsJobResult run(long sourceId, List<Entry> entries, JobContext context) {
        List<Entry> wanted = entries.stream().filter(Entry::wanted).toList();
        Tally tally = new Tally(context, wanted.size());
        ParallelReads.map(wanted, PARALLEL_PROBES, "ffprobe-", entry -> {
            context.throwIfCancellationRequested();
            tally.done(entry, failureOf(() -> apply(entry)));
            return entry;
        });
        StreamDetailsJobResult result = tally.result(sourceId, entries.size() - wanted.size());
        log.info("Stream details of source {}: {} updated, {} skipped, {} failed", sourceId, result.updated(),
                result.skipped(), result.failed());
        return result;
    }

    private StreamDetails apply(Entry entry) {
        StreamDetails probed = probe(entry.file(), entry.format());
        return entry.movie() ? movies.changeStreamDetails(entry.id(), probed)
                : episodes.changeStreamDetails(entry.id(), probed);
    }

    private StreamDetails probe(Path file, DiscFormat format) {
        try {
            return probes.current().probe(file, format);
        } catch (MediaProbeException e) {
            throw new MediaInfoProblemException(e);
        }
    }

    /**
     * @param all     whether entries with existing details are read as well
     * @param include which movies or episodes are eligible at all
     */
    private List<Entry> entries(MediaSource source, boolean all, Predicate<Long> include) {
        if (source.getMediaType() == MediaType.TV_SHOW) {
            List<EpisodeMedia> included = episodes.findBySource(source.getId()).stream()
                    .filter(media -> include.test(media.episodeId())).toList();
            return perFile(included, EpisodeMedia::file).stream()
                    .map(media -> episodeEntry(media, all))
                    .toList();
        }
        return movies.findBySource(source.getId()).stream()
                .filter(media -> include.test(media.movieId()))
                .map(media -> new Entry(media.movieId(), true, media.title(), media.file(), media.format(),
                        all || !media.hasStreamDetails()))
                .toList();
    }

    private static Entry episodeEntry(EpisodeMedia media, boolean all) {
        return new Entry(media.episodeId(), false,
                EPISODE_LABEL.formatted(media.showTitle(), media.season(), media.episode()), media.file(),
                DiscFormat.FILE, all || !media.hasStreamDetails());
    }

    /** Per file the first entry; the remaining episodes of the file receive the details as well. */
    private static <T> List<T> perFile(List<T> items, Function<T, Path> file) {
        Map<Path, T> first = new LinkedHashMap<>();
        items.forEach(item -> first.putIfAbsent(file.apply(item), item));
        return List.copyOf(first.values());
    }

    private static Optional<LocalizedText> failureOf(Runnable action) {
        try {
            action.run();
            return Optional.empty();
        } catch (ErrorResponseException e) {
            return Optional.of(e instanceof Localized localized ? localized.localizedText()
                    : LocalizedText.literal(e.getBody().getDetail()));
        }
    }

    /**
     * A movie or the first episode of a file.
     *
     * @param wanted whether the entry is supposed to be read
     */
    record Entry(long id, boolean movie, String label, Path file, DiscFormat format, boolean wanted) {
    }

    /** Counts the result and reports progress; the counters never go backwards. */
    private static final class Tally {

        private final JobContext context;
        private final int total;
        private final List<LocalizedText> failures = new ArrayList<>();
        private int updated;
        private int failed;

        private Tally(JobContext context, int total) {
            this.context = context;
            this.total = total;
        }

        synchronized void done(Entry entry, Optional<LocalizedText> failure) {
            if (failure.isPresent()) {
                failed++;
                if (failures.size() < MAX_LISTED_FAILURES) {
                    failures.add(LocalizedText.of("mediaInfo.failure")
                            .with("entry", entry.label())
                            .with("reason", failure.get()));
                }
            } else {
                updated++;
            }
            context.reportProgress(updated + failed, total, entry.label());
        }

        synchronized StreamDetailsJobResult result(long sourceId, int skipped) {
            return new StreamDetailsJobResult(sourceId, updated, skipped, failed, failures);
        }
    }
}
