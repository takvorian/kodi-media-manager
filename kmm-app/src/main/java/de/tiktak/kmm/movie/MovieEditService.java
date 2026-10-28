package de.tiktak.kmm.movie;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Speichert bearbeitete Metadaten eines Films und benennt ihn danach um, wenn das in den Einstellungen
 * eingeschaltet ist (wie Embers „Nach dem Bearbeiten umbenennen“). Das Umbenennen läuft nach dem Speichern,
 * weil es Dateien verschiebt und eine eigene Transaktion braucht.
 */
@Service
@RequiredArgsConstructor
public class MovieEditService {

    private final MovieMetadataService metadataService;
    private final MovieRenameService renameService;

    /**
     * @return der Film nach dem Speichern und einem etwaigen Umbenennen
     */
    public MovieDetails save(long movieId, MovieMetadata metadata) {
        MovieDetails saved = metadataService.update(movieId, metadata);
        return renameService.renameIfEnabled(movieId, RenameTrigger.EDIT)
                .filter(plan -> plan.status() == MovieRenamePlan.Status.RENAME)
                .map(plan -> metadataService.findDetails(movieId))
                .orElse(saved);
    }
}
