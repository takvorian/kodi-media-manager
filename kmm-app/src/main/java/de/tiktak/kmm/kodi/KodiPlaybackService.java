package de.tiktak.kmm.kodi;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import de.tiktak.kmm.kodi.rpc.KodiException;
import de.tiktak.kmm.kodi.KodiPlaybacks.Playback;
import de.tiktak.kmm.kodi.rpc.KodiPlayback;
import de.tiktak.kmm.movie.MoviePlayback;
import de.tiktak.kmm.movie.MoviePlaybackService;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.EpisodePlayback;
import de.tiktak.kmm.tvshow.EpisodePlaybackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Holt den Gesehen-Status (Wiedergaben und letzte Wiedergabe) aus Kodi und übernimmt ihn in NFO und
 * Datenbank. Kodi ergänzt nur, es setzt nichts zurück (siehe {@link KodiPlaybacks}); Einträge, die Kodi
 * nicht kennt, bleiben unverändert. Alle Kodi-Geräte nutzen dieselbe Kodi-Datenbank, deshalb genügt das
 * erste erreichbare Gerät mit passender Pfad-Zuordnung.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KodiPlaybackService {

    static final String JOB_KEY_PREFIX = "kodi-playback:";
    static final String QUERYING_MESSAGE = "Kodi wird abgefragt";
    static final int MAX_LISTED_FAILURES = 20;
    private static final String EPISODE_LABEL = "%s S%02dE%02d";
    private static final int UNKNOWN_TOTAL = 0;

    private final KodiHostService hostService;
    private final MediaSourceService sourceService;
    private final MoviePlaybackService moviePlaybacks;
    private final EpisodePlaybackService episodePlaybacks;
    private final KodiClient kodiClient;
    private final JobService jobService;
    private final Clock clock;

    /** Übernimmt den Gesehen-Status aller Filme bzw. Episoden der Quelle als Hintergrund-Job. */
    public JobSnapshot startSourceImport(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        String title = "Gesehen-Status „%s“ aus Kodi".formatted(source.getName());
        return jobService.submit(JOB_KEY_PREFIX + sourceId, title, context -> importSource(source, context));
    }

    /**
     * @throws KodiProblemException wenn kein Kodi-Gerät den Film zuordnen kann oder keines erreichbar ist
     */
    public KodiPlaybackResult importMovie(long movieId) {
        return importSingle(movieEntry(moviePlaybacks.find(movieId)), kodiClient::moviePlaybacks);
    }

    /**
     * @throws KodiProblemException wenn kein Kodi-Gerät die Episode zuordnen kann oder keines erreichbar ist
     */
    public KodiPlaybackResult importEpisode(long episodeId) {
        return importSingle(episodeEntry(episodePlaybacks.find(episodeId)), kodiClient::episodePlaybacks);
    }

    KodiPlaybackImportResult importSource(MediaSource source, JobContext context) {
        context.reportProgress(0, UNKNOWN_TOTAL, QUERYING_MESSAGE);
        boolean tvShows = source.getMediaType() == MediaType.TV_SHOW;
        Fetched fetched = fetch(source.getPath(),
                tvShows ? kodiClient::episodePlaybacks : kodiClient::moviePlaybacks);
        List<Entry> entries = tvShows
                ? episodePlaybacks.findBySource(source.getId()).stream().map(this::episodeEntry).toList()
                : moviePlaybacks.findBySource(source.getId()).stream().map(this::movieEntry).toList();
        Tally tally = new Tally();
        for (Entry entry : entries) {
            context.throwIfCancellationRequested();
            context.reportProgress(tally.count(), entries.size(), entry.label());
            tally.add(apply(entry, fetched));
        }
        KodiPlaybackImportResult result = tally.result(source.getId(), fetched.target().name());
        log.info("Playback of source '{}' from Kodi '{}': {} updated, {} unchanged, {} kept, {} not in Kodi, "
                        + "{} failed", source.getName(), result.host(), result.updated(), result.unchanged(),
                result.kept(), result.notInKodi(), result.failed());
        return result;
    }

    private KodiPlaybackResult importSingle(Entry entry, BiFunction<KodiEndpoint, String, List<KodiPlayback>> query) {
        Fetched fetched = fetch(Path.of(entry.filePath()).getParent().toString(), query);
        Applied applied = apply(entry, fetched);
        log.info("Playback from Kodi '{}': {}", fetched.target().name(), applied.message());
        return new KodiPlaybackResult(fetched.target().name(), applied.outcome(), applied.message(),
                applied.playCount(), applied.lastPlayedAt());
    }

    /** Fragt das erste erreichbare Kodi-Gerät, das das Verzeichnis zuordnen kann. */
    private Fetched fetch(String localDirectory, BiFunction<KodiEndpoint, String, List<KodiPlayback>> query) {
        KodiException lastFailure = null;
        for (KodiTarget target : hostService.targets()) {
            Optional<String> kodiDirectory = target.pathMapper().toKodiDirectory(localDirectory);
            if (kodiDirectory.isEmpty()) {
                continue;
            }
            try {
                return new Fetched(target, new KodiPlaybackIndex(query.apply(target.endpoint(), kodiDirectory.get())));
            } catch (KodiException e) {
                log.warn("Reading playback from Kodi '{}' failed: {}", target.name(), e.getMessage());
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw KodiProblemException.unavailable(lastFailure);
        }
        throw KodiProblemException.noHostFor(localDirectory);
    }

    private Applied apply(Entry entry, Fetched fetched) {
        Optional<KodiPlayback> kodi = fetched.target().pathMapper().toKodiPath(entry.filePath())
                .flatMap(kodiFile -> fetched.index().find(kodiFile, playback -> entry.matches(playback, kodiFile)));
        if (kodi.isEmpty()) {
            return entry.unchanged(KodiPlaybackOutcome.NOT_IN_KODI,
                    "„%s“ ist in Kodi nicht bekannt.".formatted(entry.label()));
        }
        if (KodiPlaybacks.isCurrent(entry.playCount(), entry.lastPlayedAt(), kodi.get(), clock.getZone())) {
            return entry.unchanged(KodiPlaybackOutcome.UNCHANGED,
                    "„%s“ ist bereits auf dem Stand von Kodi.".formatted(entry.label()));
        }
        Playback merged = KodiPlaybacks.merge(entry.playCount(), entry.lastPlayedAt(), kodi.get(), clock.getZone());
        if (merged.isSameAs(entry.playCount(), entry.lastPlayedAt())) {
            return entry.unchanged(KodiPlaybackOutcome.KEPT,
                    "„%s“: KMM kennt mehr Wiedergaben als Kodi, der Stand bleibt.".formatted(entry.label()));
        }
        try {
            entry.change().accept(merged.playCount(), merged.lastPlayedAt());
        } catch (ErrorResponseException e) {
            return entry.unchanged(KodiPlaybackOutcome.FAILED,
                    "„%s“: %s".formatted(entry.label(), e.getBody().getDetail()));
        }
        String state = merged.playCount() == null || merged.playCount() == 0 ? "ungesehen"
                : "%d× gesehen".formatted(merged.playCount());
        return new Applied(KodiPlaybackOutcome.UPDATED, "„%s“ aus Kodi übernommen: %s.".formatted(entry.label(),
                state), merged.playCount(), merged.lastPlayedAt());
    }

    private Entry movieEntry(MoviePlayback movie) {
        return new Entry(movie.title(), movie.filePath(), movie.playCount(), movie.lastPlayedAt(), null, null,
                (playCount, lastPlayedAt) -> moviePlaybacks.changePlayback(movie.movieId(), playCount, lastPlayedAt));
    }

    private Entry episodeEntry(EpisodePlayback episode) {
        return new Entry(EPISODE_LABEL.formatted(episode.showTitle(), episode.season(), episode.episode()),
                episode.filePath(), episode.playCount(), episode.lastPlayedAt(), episode.season(), episode.episode(),
                (playCount, lastPlayedAt) -> episodePlaybacks.changePlayback(episode.episodeId(), playCount,
                        lastPlayedAt));
    }

    private record Fetched(KodiTarget target, KodiPlaybackIndex index) {
    }

    /**
     * Ein Film oder eine Episode mit ihrem Stand in KMM.
     *
     * @param season  Staffel oder {@code null} bei Filmen
     * @param episode Episode oder {@code null} bei Filmen
     */
    private record Entry(String label, String filePath, Integer playCount, Instant lastPlayedAt, Integer season,
                         Integer episode, PlaybackChange change) {

        boolean matches(KodiPlayback playback, String kodiFile) {
            return season == null ? playback.isMovie(kodiFile) : playback.isEpisode(kodiFile, season, episode);
        }

        /** Ergebnis ohne Änderung: KMM behält seinen bisherigen Stand. */
        Applied unchanged(KodiPlaybackOutcome outcome, String message) {
            return new Applied(outcome, message, playCount, lastPlayedAt);
        }
    }

    @FunctionalInterface
    private interface PlaybackChange {
        void accept(Integer playCount, Instant lastPlayedAt);
    }

    private record Applied(KodiPlaybackOutcome outcome, String message, Integer playCount, Instant lastPlayedAt) {
    }

    private static final class Tally {

        private final List<String> failures = new ArrayList<>();
        private int updated;
        private int unchanged;
        private int kept;
        private int notInKodi;
        private int failed;

        int count() {
            return updated + unchanged + kept + notInKodi + failed;
        }

        void add(Applied applied) {
            switch (applied.outcome()) {
                case UPDATED -> updated++;
                case UNCHANGED -> unchanged++;
                case KEPT -> kept++;
                case NOT_IN_KODI -> notInKodi++;
                case FAILED -> {
                    failed++;
                    if (failures.size() < MAX_LISTED_FAILURES) {
                        failures.add(applied.message());
                    }
                }
            }
        }

        KodiPlaybackImportResult result(long sourceId, String host) {
            return new KodiPlaybackImportResult(sourceId, host, updated, unchanged, kept, notInKodi, failed,
                    failures);
        }
    }
}
