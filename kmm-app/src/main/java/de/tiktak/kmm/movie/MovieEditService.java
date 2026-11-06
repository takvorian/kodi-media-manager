package de.tiktak.kmm.movie;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Saves edited metadata of a movie and then renames it if this is enabled in the settings
 * (like Ember's "Rename after editing"). Renaming runs after saving,
 * because it moves files and needs its own transaction.
 */
@Service
@RequiredArgsConstructor
public class MovieEditService {

    private final MovieMetadataService metadataService;
    private final MovieRenameService renameService;

    /**
     * @return the movie after saving and any renaming
     */
    public MovieDetails save(long movieId, MovieMetadata metadata) {
        MovieDetails saved = metadataService.update(movieId, metadata);
        return renameService.renameIfEnabled(movieId, RenameTrigger.EDIT)
                .filter(plan -> plan.status() == MovieRenamePlan.Status.RENAME)
                .map(plan -> metadataService.findDetails(movieId))
                .orElse(saved);
    }
}
