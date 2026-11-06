package de.tiktak.kmm.theme;

import java.nio.file.Path;
import java.util.Objects;

import de.tiktak.kmm.plugin.api.MediaType;

/**
 * Movie or TV show whose theme music is located as {@code theme.mp3} in {@code directory}.
 *
 * @param originalTitle original title or {@code null}; providers often only know that one
 * @param directory     movie or TV show folder
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
