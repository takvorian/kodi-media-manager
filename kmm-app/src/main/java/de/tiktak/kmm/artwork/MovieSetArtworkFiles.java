package de.tiktak.kmm.artwork;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.settings.MovieSetSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bilddateien eines Filmsets in allen eingeschalteten Ablagen: im flachen Schema
 * ({@code <Ordner>/<Settitel>-poster.jpg}) und in Kodis Set-Ordnern ({@code <Ordner>/<Settitel>/poster.jpg}).
 * Gelesen wird aus der ersten Ablage, die ein Bild hat; geschrieben in alle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovieSetArtworkFiles {

    private static final List<String> EXTENSIONS = List.of("jpg", "jpeg", "png");
    private static final String FLAT_SEPARATOR = "-";
    private static final String EXTENSION_SEPARATOR = ".";

    private final ArtworkFiles artworkFiles;

    static boolean hasStorage(MovieSetSettings settings) {
        return settings.flatImages() || settings.kodiImages();
    }

    Optional<ArtworkFile> find(MovieSetSettings settings, String title, ImageType type) {
        return slots(settings, title, type).stream()
                .map(artworkFiles::find)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * @return die Datei in der ersten Ablage
     */
    ArtworkFile save(MovieSetSettings settings, String title, ImageType type, DownloadedImage image) {
        List<ArtworkFile> saved = new ArrayList<>();
        for (ArtworkSlot slot : slots(settings, title, type)) {
            createFolder(slot);
            saved.add(artworkFiles.save(slot, image));
        }
        return saved.getFirst();
    }

    void delete(MovieSetSettings settings, String title, ImageType type) {
        slots(settings, title, type).forEach(artworkFiles::delete);
    }

    /**
     * Verschiebt die Bilder nach einer Umbenennung des Sets auf den neuen Titel. Ein leerer alter Set-Ordner wird
     * entfernt.
     */
    void rename(MovieSetSettings settings, String previousTitle, String title, List<ImageType> types) {
        for (ImageType type : types) {
            List<ArtworkSlot> previous = slots(settings, previousTitle, type);
            List<ArtworkSlot> current = slots(settings, title, type);
            for (int slot = 0; slot < previous.size(); slot++) {
                move(previous.get(slot).candidates(), current.get(slot).candidates());
            }
        }
        if (settings.kodiImages()) {
            deleteIfEmpty(kodiFolder(settings, previousTitle));
        }
    }

    private static List<ArtworkSlot> slots(MovieSetSettings settings, String title, ImageType type) {
        String kodiName = ArtworkTypes.kodiName(type);
        List<ArtworkSlot> slots = new ArrayList<>();
        if (settings.flatImages()) {
            String name = MovieSetFileNames.flat(title) + FLAT_SEPARATOR + kodiName;
            slots.add(new ArtworkSlot(type, files(Path.of(settings.flatImagesFolder()), name)));
        }
        if (settings.kodiImages()) {
            slots.add(new ArtworkSlot(type, files(kodiFolder(settings, title), kodiName)));
        }
        return slots;
    }

    private static Path kodiFolder(MovieSetSettings settings, String title) {
        return Path.of(settings.kodiImagesFolder()).resolve(MovieSetFileNames.kodiFolder(title));
    }

    private static List<Path> files(Path folder, String name) {
        return EXTENSIONS.stream().map(extension -> folder.resolve(name + EXTENSION_SEPARATOR + extension)).toList();
    }

    private static void move(List<Path> sources, List<Path> targets) {
        for (int index = 0; index < sources.size(); index++) {
            Path source = sources.get(index);
            Path target = targets.get(index);
            if (source.equals(target) || !Files.isRegularFile(source)) {
                continue;
            }
            try {
                Files.createDirectories(target.getParent());
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("Moved movie set image {} to {}", source, target);
            } catch (IOException e) {
                throw ArtworkProblemException.unwritable(target, e);
            }
        }
    }

    private static void createFolder(ArtworkSlot slot) {
        Path folder = slot.candidates().getFirst().getParent();
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw ArtworkProblemException.unwritable(folder, e);
        }
    }

    private static void deleteIfEmpty(Path folder) {
        if (!Files.isDirectory(folder)) {
            return;
        }
        try (var entries = Files.list(folder)) {
            if (entries.findAny().isEmpty()) {
                Files.delete(folder);
            }
        } catch (IOException e) {
            log.warn("Movie set folder {} could not be removed: {}", folder, e.getMessage());
        }
    }
}
