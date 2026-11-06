package de.tiktak.kmm.kodi;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
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
 * Fetches the watched status (plays and last play) and the user rating from Kodi and takes them over into NFO and
 * database. In the NFO the rating survives when Kodi reads the entry again. Kodi only adds, it does not reset anything
 * (see {@link KodiPlaybacks}); entries that Kodi does not know stay unchanged. All Kodi devices use the same Kodi
 * database, so the first reachable device with a matching path mapping is enough.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KodiPlaybackService {

    static final String JOB_KEY_PREFIX = "kodi-playback:";
    static final LocalizedText QUERYING_MESSAGE = LocalizedText.of("kodi.playback.querying");
    static final int MAX_LISTED_FAILURES = 20;
    private static final String EPISODE_LABEL = "%s S%02dE%02d";
    private static final int UNKNOWN_TOTAL = 0;
    /** Value of the argument {@code rating} without a user rating. */
    private static final String NO_RATING = "none";

    private final KodiHostService hostService;
    private final MediaSourceService sourceService;
    private final MoviePlaybackService moviePlaybacks;
    private final EpisodePlaybackService episodePlaybacks;
    private final KodiClient kodiClient;
    private final JobService jobService;
    private final Clock clock;

    /** Takes over the watched status of all movies or episodes of the source as a background job. */
    public JobSnapshot startSourceImport(long sourceId) {
        MediaSource source = sourceService.require(sourceId);
        LocalizedText title = LocalizedText.of("kodi.playback.jobTitle").with("source", source.getName());
        return jobService.submit(JOB_KEY_PREFIX + sourceId, title, context -> importSource(source, context));
    }

    /**
     * @throws KodiProblemException if no Kodi device can map the movie or none can be reached
     */
    public KodiPlaybackResult importMovie(long movieId) {
        return importSingle(movieEntry(moviePlaybacks.find(movieId)), kodiClient::moviePlaybacks);
    }

    /**
     * @throws KodiProblemException if no Kodi device can map the episode or none can be reached
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
        log.info("Playback from Kodi '{}': {}", fetched.target().name(), Messages.english(applied.message()));
        Playback playback = applied.playback();
        return new KodiPlaybackResult(fetched.target().name(), applied.outcome(), applied.message(),
                playback.playCount(), playback.lastPlayedAt(), playback.userRating());
    }

    /** Asks the first reachable Kodi device that can map the directory. */
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
            return entry.unchanged(KodiPlaybackOutcome.NOT_IN_KODI, entry.text("kodi.playback.notInKodi"));
        }
        if (KodiPlaybacks.isCurrent(entry.playback(), kodi.get(), clock.getZone())) {
            return entry.unchanged(KodiPlaybackOutcome.UNCHANGED, entry.text("kodi.playback.unchanged"));
        }
        Playback merged = KodiPlaybacks.merge(entry.playback(), kodi.get(), clock.getZone());
        if (merged.equals(entry.playback())) {
            return entry.unchanged(KodiPlaybackOutcome.KEPT, entry.text("kodi.playback.kept"));
        }
        try {
            entry.change().accept(merged);
        } catch (ErrorResponseException e) {
            return entry.unchanged(KodiPlaybackOutcome.FAILED,
                    entry.text("kodi.playback.failed").with("reason", reason(e)));
        }
        LocalizedText updated = entry.text("kodi.playback.updated")
                .with("playCount", merged.playCount() == null ? 0 : merged.playCount())
                .with("rating", merged.userRating() == null ? NO_RATING : merged.userRating());
        return new Applied(KodiPlaybackOutcome.UPDATED, updated, merged);
    }

    private static LocalizedText reason(ErrorResponseException e) {
        return e instanceof Localized localized ? localized.localizedText()
                : LocalizedText.literal(e.getBody().getDetail());
    }

    private Entry movieEntry(MoviePlayback movie) {
        return new Entry(movie.title(), movie.filePath(),
                new Playback(movie.playCount(), movie.lastPlayedAt(), movie.userRating()), null, null,
                merged -> moviePlaybacks.changePlayback(movie.movieId(), merged.playCount(), merged.lastPlayedAt(),
                        merged.userRating()));
    }

    private Entry episodeEntry(EpisodePlayback episode) {
        return new Entry(EPISODE_LABEL.formatted(episode.showTitle(), episode.season(), episode.episode()),
                episode.filePath(), new Playback(episode.playCount(), episode.lastPlayedAt(), episode.userRating()),
                episode.season(), episode.episode(),
                merged -> episodePlaybacks.changePlayback(episode.episodeId(), merged.playCount(),
                        merged.lastPlayedAt(), merged.userRating()));
    }

    private record Fetched(KodiTarget target, KodiPlaybackIndex index) {
    }

    /**
     * A movie or an episode with its state in KMM.
     *
     * @param season  season, or {@code null} for movies
     * @param episode episode, or {@code null} for movies
     */
    private record Entry(String label, String filePath, Playback playback, Integer season, Integer episode,
                         PlaybackChange change) {

        boolean matches(KodiPlayback playback, String kodiFile) {
            return season == null ? playback.isMovie(kodiFile) : playback.isEpisode(kodiFile, season, episode);
        }

        /** Result without change: KMM keeps its previous state. */
        Applied unchanged(KodiPlaybackOutcome outcome, LocalizedText message) {
            return new Applied(outcome, message, playback);
        }

        /** A message about this entry. */
        LocalizedText text(String key) {
            return LocalizedText.of(key).with("label", label);
        }
    }

    @FunctionalInterface
    private interface PlaybackChange {
        void accept(Playback merged);
    }

    private record Applied(KodiPlaybackOutcome outcome, LocalizedText message, Playback playback) {
    }

    private static final class Tally {

        private final List<LocalizedText> failures = new ArrayList<>();
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
