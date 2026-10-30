package de.tiktak.kmm.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import de.tiktak.kmm.migration.ember.kodi.EmberKodiHostsReader;
import de.tiktak.kmm.migration.ember.settings.EmberAdvancedSettingsReader;
import lombok.extern.slf4j.Slf4j;

/**
 * Hält das hochgeladene Ember-Profil, bis der Import es nicht mehr braucht. Ember läuft meist auf einem anderen
 * Rechner als KMM. Es gibt nur ein Profil zur Zeit, ein neues ersetzt das alte. Die Dateien enthalten Zugangsdaten
 * und bleiben deshalb nicht liegen: Reste eines abgebrochenen Ablaufs löscht der nächste Start.
 */
@Slf4j
@Component
class EmberProfileUpload {

    static final String DIRECTORY_NAME = "ember-upload";

    private static final Pattern DATABASE_FILE = Pattern.compile("MyVideos\\d+\\.emm", Pattern.CASE_INSENSITIVE);
    private static final Set<String> OPTIONAL_FILES = Set.of("Settings.xml", EmberAdvancedSettingsReader.FILE_NAME,
            EmberKodiHostsReader.FILE_NAME);

    private final Path directory;

    EmberProfileUpload(@Value("${kmm.home}") Path home) {
        directory = home.resolve(DIRECTORY_NAME);
    }

    /**
     * Ersetzt das hochgeladene Profil.
     *
     * @throws EmberProfileUploadException wenn eine unerwartete Datei dabei ist oder die Datenbank fehlt
     */
    void store(List<MultipartFile> files) {
        List<String> names = files.stream().map(EmberProfileUpload::fileName).toList();
        if (names.stream().noneMatch(name -> DATABASE_FILE.matcher(name).matches())) {
            throw new EmberProfileUploadException("In den hochgeladenen Dateien ist keine Ember-Datenbank.");
        }
        discard();
        try {
            Files.createDirectories(directory);
            for (MultipartFile file : files) {
                file.transferTo(directory.resolve(fileName(file)));
            }
        } catch (IOException e) {
            discard();
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @throws EmberProfileUploadException wenn kein Profil hochgeladen ist
     */
    Path directory() {
        if (!Files.isDirectory(directory)) {
            throw new EmberProfileUploadException("Bitte zuerst das Ember-Profil hochladen.");
        }
        return directory;
    }

    @EventListener(ApplicationReadyEvent.class)
    void discard() {
        try {
            FileSystemUtils.deleteRecursively(directory);
        } catch (IOException e) {
            log.warn("Uploaded Ember profile could not be deleted", e);
        }
    }

    /** Nur bekannte Namen, damit kein Dateiname aus dem Upload-Ordner hinausführt. */
    private static String fileName(MultipartFile file) {
        String name = String.valueOf(file.getOriginalFilename());
        if (!OPTIONAL_FILES.contains(name) && !DATABASE_FILE.matcher(name).matches()) {
            throw new EmberProfileUploadException("'%s' gehört nicht zu einem Ember-Profil.".formatted(name));
        }
        return name;
    }
}
