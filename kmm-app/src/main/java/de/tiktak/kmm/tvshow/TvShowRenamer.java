package de.tiktak.kmm.tvshow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.nfo.EpisodeStreamDetails;
import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.tvshow.TvShowFileMover.Relocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Benennt eine Serie nach ihrem Plan um: zuerst im Dateisystem, dann in der Datenbank. Externe Untertitel stehen
 * mit Pfad in den technischen Angaben; sie werden in Datenbank und Episoden-NFO nachgezogen.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TvShowRenamer {

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final TvShowNfoFiles nfoFiles;
    private final ApplicationEventPublisher events;

    /**
     * @param plan ein Plan mit Status {@link TvShowRenamePlan.Status#RENAME}
     * @throws IOException wenn das Dateisystem die Änderung ablehnt; dann ist nichts geändert
     */
    @Transactional
    public void rename(TvShowRenamePlan plan, Path sourceRoot) throws IOException {
        TvShow show = showRepository.findById(plan.showId())
                .orElseThrow(() -> TvShowProblemException.showNotFound(plan.showId()));
        List<Episode> episodes = episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(show.getId());
        Relocation relocation = TvShowFileMover.move(plan, sourceRoot);
        show.relocate(relocation.directory().toString());
        episodes.forEach(episode -> episode.relocate(relocation.relocate(episode.file()).toString()));
        episodes.stream()
                .collect(Collectors.groupingBy(Episode::getFilePath))
                .forEach((file, fileEpisodes) -> relocateSubtitles(Path.of(file), fileEpisodes, relocation));
        events.publishEvent(new TvShowRenamedEvent(show.getId(), show.getSourceId(), plan.currentDirectory(),
                plan.targetDirectory()));
        log.info("TV show {} renamed: {} -> {}, {} episode file(s)", show.getId(), plan.currentDirectory(),
                plan.targetDirectory(), plan.files().stream()
                        .filter(file -> file.status() == TvShowRenamePlan.Status.RENAME).count());
    }

    private void relocateSubtitles(Path file, List<Episode> fileEpisodes, Relocation relocation) {
        Map<Episode, StreamDetails> relocated = fileEpisodes.stream()
                .filter(Episode::hasStreamDetails)
                .collect(Collectors.toMap(episode -> episode, episode -> relocated(episode.streamDetails(), relocation)));
        boolean changed = relocated.entrySet().stream()
                .anyMatch(entry -> !entry.getValue().equals(entry.getKey().streamDetails()));
        if (!changed) {
            return;
        }
        relocated.forEach(Episode::applyStreamDetails);
        nfoFiles.findEpisodeNfo(file).ifPresent(nfo -> {
            try {
                TvNfoState written = nfoFiles.writeStreamDetails(nfo, relocated.entrySet().stream()
                        .map(entry -> new EpisodeStreamDetails(entry.getKey().getSeason(), entry.getKey().getEpisode(),
                                entry.getValue()))
                        .toList());
                fileEpisodes.stream()
                        .filter(episode -> nfo.modifiedAt().equals(episode.getNfoModifiedAt()))
                        .forEach(episode -> episode.markNfoSynchronized(written.modifiedAt()));
            } catch (NfoException e) {
                log.warn("Subtitle paths not updated in {}: {}", nfo.file(), e.getMessage());
            }
        });
    }

    private static StreamDetails relocated(StreamDetails streams, Relocation relocation) {
        return new StreamDetails(streams.videos(), streams.audios(), streams.subtitles().stream()
                .map(subtitle -> subtitle.path() == null ? subtitle : new StreamDetails.Subtitle(subtitle.language(),
                        subtitle.forced(), relocation.relocate(Path.of(subtitle.path())).toString()))
                .toList());
    }
}
