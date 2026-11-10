package de.tiktak.kmm.tvshow;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Removes episodes or a whole TV show from the database and tells Kodi about it after the commit. Credits, ratings,
 * genres and the other details go with them.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class TvShowRemover {

    private final TvShowRepository showRepository;
    private final EpisodeRepository episodeRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void remove(TvShowRemoval removal) {
        if (removal.wholeShow()) {
            removeShow(removal);
        } else {
            removeEpisodes(removal);
        }
    }

    private void removeShow(TvShowRemoval removal) {
        episodeRepository.deleteAll(episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(removal.showId()));
        showRepository.findById(removal.showId()).ifPresent(showRepository::delete);
        events.publishEvent(new TvShowRemovedEvent(removal.showTitle(), removal.directory()));
        log.info("TV show {} removed from the library: {}", removal.showId(), removal.directory());
    }

    /** Seasons without episodes afterwards lose their details as well. */
    private void removeEpisodes(TvShowRemoval removal) {
        episodeRepository.deleteAll(episodeRepository.findAllById(removal.episodeIds()));
        showRepository.findById(removal.showId()).ifPresent(show -> {
            Set<Integer> remaining = episodeRepository.findByTvShowIdOrderBySeasonAscEpisodeAsc(show.getId()).stream()
                    .map(Episode::getSeason)
                    .collect(Collectors.toSet());
            removal.seasons().stream().filter(season -> !remaining.contains(season)).forEach(show::removeSeason);
        });
        events.publishEvent(new EpisodesRemovedEvent(removal.showTitle(), removal.episodes()));
        log.info("{} episode(s) of TV show {} removed from the library", removal.episodeIds().size(),
                removal.showId());
    }
}
