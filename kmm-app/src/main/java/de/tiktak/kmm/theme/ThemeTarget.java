package de.tiktak.kmm.theme;

import java.nio.file.Path;
import java.util.Objects;

import de.tiktak.kmm.plugin.api.MediaType;

/**
 * Film oder Serie, deren Titelmusik als {@code theme.mp3} in {@code directory} liegt.
 *
 * @param originalTitle Originaltitel oder {@code null}; die Anbieter kennen oft nur ihn
 * @param directory     Film- bzw. Serienordner
 */
record ThemeTarget(MediaType mediaType, String title, String originalTitle, Integer year, Path directory) {

    static final String FILE_NAME = "theme.mp3";

    ThemeTarget {
        Objects.requireNonNull(mediaType, "mediaType must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(directory, "directory must not be null");
    }

    Path themeFile() {
        return directory.resolve(FILE_NAME);
    }
}
