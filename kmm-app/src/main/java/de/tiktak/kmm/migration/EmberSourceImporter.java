package de.tiktak.kmm.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.i18n.Localized;
import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.ImportedSource;
import de.tiktak.kmm.source.MediaSourceConflictException;
import de.tiktak.kmm.source.MediaSourceService;
import lombok.RequiredArgsConstructor;

/**
 * Imports a movie or TV show source from Ember. An existing source with the same directory is reused.
 */
@Component
@RequiredArgsConstructor
class EmberSourceImporter {

    private final MediaSourceService sourceService;

    /**
     * @param emberPath directory as Ember stored it, or {@code null}
     * @param language  language of the source in Ember, or {@code null}
     * @return ID of the source in KMM; empty if the source could not be imported
     */
    Optional<Long> importSource(String name, String emberPath, String language, MediaType mediaType,
            EmberPathTranslator paths, EmberImportProgress progress) {
        Optional<String> directory = emberPath == null ? Optional.empty() : paths.translate(emberPath);
        if (directory.isEmpty()) {
            progress.sourceSkipped(LocalizedText.of("migration.sourceSkipped.notMapped").with("name", name)
                    .with("path", emberPath));
            return Optional.empty();
        }
        try {
            ImportedSource imported = sourceService.importSource(name, directory.get(), mediaType,
                    EmberMappings.language(language));
            progress.sourceImported(imported.created());
            if (!Files.isDirectory(Path.of(directory.get()))) {
                progress.warn(LocalizedText.of("migration.warning.sourceUnreachable")
                        .with("directory", directory.get()).with("name", name));
            }
            return Optional.of(imported.source().getId());
        } catch (MediaSourceConflictException e) {
            progress.sourceSkipped(LocalizedText.of("migration.sourceSkipped.conflict").with("name", name)
                    .with("path", directory.get()).with("reason", reason(e)));
            return Optional.empty();
        }
    }

    /** The conflict is translatable as soon as the source module delivers it as {@link Localized}. */
    private static LocalizedText reason(MediaSourceConflictException e) {
        return e instanceof Localized localized ? localized.localizedText()
                : LocalizedText.literal(e.getBody().getDetail());
    }
}
