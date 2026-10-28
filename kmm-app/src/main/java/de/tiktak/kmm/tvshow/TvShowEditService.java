package de.tiktak.kmm.tvshow;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Speichert bearbeitete Metadaten einer Serie oder Episode und benennt die Serie danach um, wenn das in den
 * Einstellungen eingeschaltet ist. Das Umbenennen läuft nach dem Speichern, weil es Dateien verschiebt und eine
 * eigene Transaktion braucht.
 */
@Service
@RequiredArgsConstructor
public class TvShowEditService {

    private final TvShowMetadataService metadataService;
    private final TvShowRenameService renameService;

    /** @return die Serie nach dem Speichern und einem etwaigen Umbenennen */
    public TvShowDetails saveShow(long showId, TvShowMetadata metadata) {
        TvShowDetails saved = metadataService.updateShow(showId, metadata);
        return renamed(showId) ? metadataService.findShow(showId) : saved;
    }

    /** @return die Episode nach dem Speichern und einem etwaigen Umbenennen ihrer Serie */
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
