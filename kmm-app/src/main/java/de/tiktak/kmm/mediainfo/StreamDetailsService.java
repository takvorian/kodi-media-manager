package de.tiktak.kmm.mediainfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

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
 * Liest technische Angaben mit ffprobe aus und übernimmt sie in NFO und Datenbank. Episoden einer
 * Mehrfachepisoden-Datei werden zusammen ausgelesen, denn sie teilen sich die Datei.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamDetailsService {

    static final String JOB_KEY_PREFIX = "stream-details:";
    static final String NEW_FILES_JOB_KEY_PREFIX = "stream-details-new:";
    static final int MAX_LISTED_FAILURES = 20;
    /** Gleichzeitige ffprobe-Aufrufe; jeder liest nur den Anfang der Datei, mehr belastet das NAS. */
    static final int PARALLEL_PROBES = 4;
    private static final String EPISODE_LABEL = "%s S%02dE%02d";

    private final MediaSourceService sourceService;
    private final MovieStreamDetailsService movies;
    private final EpisodeStreamDetailsService episodes;
    private final MediaProbes probes;
    private final JobService jobService;

    /**
     * @throws MediaInfoProblemException wenn ffprobe die Datei nicht lesen kann
     */
    public StreamDetails probeMovie(long movieId) {
        MovieMedia media = movies.find(movieId);
        return movies.changeStreamDetails(movieId, probe(media.file(), media.format()));
    }

    /**
     * @throws MediaInfoProblemException wenn ffprobe die Datei nicht lesen kann
     */
    public StreamDetails probeEpisode(long episodeId) {
        EpisodeMedia media = episodes.find(episodeId);
        return episodes.changeStreamDetails(episodeId, probe(media.file(), DiscFormat.FILE));
    }

    /** Liest die Filme bzw. Episoden der Quelle als Hintergrund-Job aus. */
    public JobSnapshot startSource(long sourceId, StreamDetailsScope scope) {
        MediaSource source = sourceService.require(sourceId);
        String title = "Technische Angaben „%s“".formatted(source.getName());
        return jobService.submit(JOB_KEY_PREFIX + sourceId, title,
                context -> run(sourceId, entries(source, scope == StreamDetailsScope.ALL, id -> true), context));
    }

    /**
     * Liest neu gefundene Dateien aus, wenn das eingestellt ist; läuft schon ein solcher Job für die
     * Quelle, holt der nächste Scan die übrigen nach.
     */
    void startNewFiles(long sourceId, MediaType mediaType, Collection<Long> ids) {
        if (ids.isEmpty() || !probes.probeNewFiles()) {
            return;
        }
        MediaSource source = sourceService.require(sourceId);
        Set<Long> wanted = Set.copyOf(ids);
        List<Entry> entries = entries(source, true, wanted::contains);
        String title = "Technische Angaben neuer Dateien „%s“".formatted(source.getName());
        try {
            jobService.submit(NEW_FILES_JOB_KEY_PREFIX + sourceId, title, context -> run(sourceId, entries, context));
        } catch (JobAlreadyRunningException e) {
            log.info("Stream details of new files in source {} are already being read", sourceId);
        }
        log.debug("Reading stream details of {} new {} file(s)", entries.size(), mediaType);
    }

    /**
     * Liest einen Film nach dem Scrapen aus, wenn das eingestellt ist.
     *
     * @return die übernommenen Angaben; leer, wenn das Auslesen nach dem Scrapen abgeschaltet ist
     * @throws MediaInfoProblemException wenn ffprobe die Datei nicht lesen kann
     */
    public Optional<StreamDetails> probeMovieIfEnabled(long movieId) {
        return probes.probeOnScrape() ? Optional.of(probeMovie(movieId)) : Optional.empty();
    }

    /**
     * Liest einen Film nach dem automatischen Scrapen aus, wenn das eingestellt ist.
     *
     * @return eine Fehlermeldung, wenn das Auslesen scheiterte
     */
    public Optional<String> probeMovieAfterScrape(long movieId) {
        return failureOf(() -> probeMovieIfEnabled(movieId));
    }

    /**
     * Liest nach dem Scrapen einer Serie die Episoden ohne technische Angaben aus, wenn das eingestellt
     * ist. Vorhandene Angaben, etwa aus Ember, bleiben, damit das Scrapen schnell bleibt.
     *
     * @return leer, wenn das Auslesen nach dem Scrapen abgeschaltet ist
     */
    public Optional<ShowStreamDetailsResult> probeShowIfEnabled(long showId) {
        return probeEpisodesIfEnabled(episodes.findByShow(showId));
    }

    private Optional<ShowStreamDetailsResult> probeEpisodesIfEnabled(List<EpisodeMedia> candidates) {
        if (!probes.probeOnScrape()) {
            return Optional.empty();
        }
        int updated = 0;
        List<String> failures = new ArrayList<>();
        for (EpisodeMedia media : perFile(candidates, EpisodeMedia::file)) {
            if (!media.hasStreamDetails()) {
                Optional<String> failure = failureOf(() -> probeEpisode(media.episodeId()));
                failure.ifPresent(failures::add);
                updated += failure.isEmpty() ? 1 : 0;
            }
        }
        return Optional.of(new ShowStreamDetailsResult(updated, failures));
    }

    /**
     * Liest nach dem automatischen Scrapen einer Serie die Episoden ohne technische Angaben aus.
     *
     * @return die Fehlermeldungen der Dateien, die nicht gelesen werden konnten
     * @see #probeShowIfEnabled(long)
     */
    public List<String> probeEpisodesAfterScrape(long showId) {
        return probeShowIfEnabled(showId).map(ShowStreamDetailsResult::failures).orElse(List.of());
    }

    /**
     * Wie {@link #probeEpisodesAfterScrape(long)}, aber nur für die Episoden einer Staffel.
     *
     * @return die Fehlermeldungen der Dateien, die nicht gelesen werden konnten
     */
    public List<String> probeSeasonAfterScrape(long showId, int season) {
        return probeEpisodesIfEnabled(episodes.findByShow(showId).stream()
                .filter(media -> media.season() == season)
                .toList())
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
     * @param all     ob auch Einträge mit vorhandenen Angaben ausgelesen werden
     * @param include welche Filme bzw. Episoden überhaupt in Frage kommen
     */
    private List<Entry> entries(MediaSource source, boolean all, Predicate<Long> include) {
        if (source.getMediaType() == MediaType.TV_SHOW) {
            List<EpisodeMedia> included = episodes.findBySource(source.getId()).stream()
                    .filter(media -> include.test(media.episodeId())).toList();
            return perFile(included, EpisodeMedia::file).stream()
                    .map(media -> new Entry(media.episodeId(), false,
                            EPISODE_LABEL.formatted(media.showTitle(), media.season(), media.episode()), media.file(),
                            DiscFormat.FILE, all || !media.hasStreamDetails()))
                    .toList();
        }
        return movies.findBySource(source.getId()).stream()
                .filter(media -> include.test(media.movieId()))
                .map(media -> new Entry(media.movieId(), true, media.title(), media.file(), media.format(),
                        all || !media.hasStreamDetails()))
                .toList();
    }

    /** Je Datei der erste Eintrag; die übrigen Episoden der Datei bekommen die Angaben mit. */
    private static <T> List<T> perFile(List<T> items, Function<T, Path> file) {
        Map<Path, T> first = new LinkedHashMap<>();
        items.forEach(item -> first.putIfAbsent(file.apply(item), item));
        return List.copyOf(first.values());
    }

    private static Optional<String> failureOf(Runnable action) {
        try {
            action.run();
            return Optional.empty();
        } catch (ErrorResponseException e) {
            return Optional.of(e.getBody().getDetail());
        }
    }

    /**
     * Ein Film oder die erste Episode einer Datei.
     *
     * @param wanted ob der Eintrag ausgelesen werden soll
     */
    record Entry(long id, boolean movie, String label, Path file, DiscFormat format, boolean wanted) {
    }

    /** Zählt das Ergebnis und meldet den Fortschritt; die Zähler steigen nie rückwärts. */
    private static final class Tally {

        private final JobContext context;
        private final int total;
        private final List<String> failures = new ArrayList<>();
        private int updated;
        private int failed;

        private Tally(JobContext context, int total) {
            this.context = context;
            this.total = total;
        }

        synchronized void done(Entry entry, Optional<String> failure) {
            if (failure.isPresent()) {
                failed++;
                if (failures.size() < MAX_LISTED_FAILURES) {
                    failures.add("%s: %s".formatted(entry.label(), failure.get()));
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
