package de.tiktak.kmm.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.ImportedSource;
import de.tiktak.kmm.source.MediaSourceConflictException;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;

/**
 * Übernimmt eine Film- oder Serienquelle aus Ember. Eine vorhandene Quelle mit demselben Verzeichnis
 * wird weiterverwendet.
 */
@Component
@RequiredArgsConstructor
class EmberSourceImporter {

    private final MediaSourceService sourceService;

    /**
     * @param emberPath Verzeichnis, wie Ember es gespeichert hat, oder {@code null}
     * @param language  Sprache der Quelle in Ember oder {@code null}
     * @return ID der Quelle in KMM; leer, wenn die Quelle nicht übernommen werden konnte
     */
    Optional<Long> importSource(String name, String emberPath, String language, MediaType mediaType,
            EmberPathTranslator paths, EmberImportProgress progress) {
        Optional<String> directory = emberPath == null ? Optional.empty() : paths.translate(emberPath);
        if (directory.isEmpty()) {
            progress.sourceSkipped("%s (%s): keine gültige Pfad-Zuordnung auf diesem System".formatted(name, emberPath));
            return Optional.empty();
        }
        try {
            ImportedSource imported = sourceService.importSource(name, directory.get(), mediaType,
                    EmberMappings.language(language));
            progress.sourceImported(imported.created());
            if (!Files.isDirectory(Path.of(directory.get()))) {
                progress.warn("Das Verzeichnis %s der Quelle „%s“ ist derzeit nicht erreichbar."
                        .formatted(directory.get(), name));
            }
            return Optional.of(imported.source().getId());
        } catch (MediaSourceConflictException e) {
            progress.sourceSkipped("%s (%s): %s".formatted(name, directory.get(), e.getBody().getDetail()));
            return Optional.empty();
        }
    }
}
