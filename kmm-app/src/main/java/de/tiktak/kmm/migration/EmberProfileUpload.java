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

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHostsReader;
import de.tiktak.kmm.migration.ember.settings.EmberAdvancedSettingsReader;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the uploaded Ember profile until the import no longer needs it. Ember usually runs on another computer than
 * KMM. There is only one profile at a time, a new one replaces the old one. The files contain credentials and are
 * therefore not kept: the next start deletes leftovers of an aborted run.
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
     * Replaces the uploaded profile.
     *
     * @throws EmberProfileUploadException if an unexpected file is included or the database is missing
     */
    void store(List<MultipartFile> files) {
        List<String> names = files.stream().map(EmberProfileUpload::fileName).toList();
        if (names.stream().noneMatch(name -> DATABASE_FILE.matcher(name).matches())) {
            throw new EmberProfileUploadException(LocalizedText.of("migration.upload.noDatabase"));
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
     * @throws EmberProfileUploadException if no profile is uploaded
     */
    Path directory() {
        if (!Files.isDirectory(directory)) {
            throw new EmberProfileUploadException(LocalizedText.of("migration.upload.missing"));
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

    /** Only known names, so that no file name leads out of the upload folder. */
    private static String fileName(MultipartFile file) {
        String name = String.valueOf(file.getOriginalFilename());
        if (!OPTIONAL_FILES.contains(name) && !DATABASE_FILE.matcher(name).matches()) {
            throw new EmberProfileUploadException(LocalizedText.of("migration.upload.foreignFile").with("name", name));
        }
        return name;
    }
}
