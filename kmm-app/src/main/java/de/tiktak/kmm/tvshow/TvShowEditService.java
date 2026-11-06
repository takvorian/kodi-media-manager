package de.tiktak.kmm.tvshow;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Saves edited metadata of a TV show or episode and then renames the show if that is enabled in the settings.
 * Renaming runs after saving because it moves files and needs its own transaction.
 */
@Service
@RequiredArgsConstructor
public class TvShowEditService {

    private final TvShowMetadataService metadataService;
    private final TvShowRenameService renameService;

    /** @return the TV show after saving and any renaming */
    public TvShowDetails saveShow(long showId, TvShowMetadata metadata) {
        TvShowDetails saved = metadataService.updateShow(showId, metadata);
        return renamed(showId) ? metadataService.findShow(showId) : saved;
    }

    /** @return the episode after saving and any renaming of its TV show */
    public EpisodeDetails saveEpisode(long episodeId, EpisodeMetadata metadata) {
        EpisodeDetails saved = metadataService.updateEpisode(episodeId, metadata);
        return renamed(saved.tvShowId()) ? metadataService.findEpisode(episodeId) : saved;
    }

    private boolean renamed(long showId) {
        return renameService.renameIfEnabled(showId, TvRenameTrigger.EDIT)
                .filter(plan -> plan.status() == TvShowRenamePlan.Status.RENAME)
                .isPresent();
    }
}
