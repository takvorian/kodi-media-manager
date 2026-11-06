package de.tiktak.kmm.settings;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import de.tiktak.kmm.scanner.ScanRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * User settings of KMM. Lists are cleaned up (trimmed, without empty entries and duplicates),
 * file extensions without a dot and in lower case.
 *
 * @param metadataLanguage language of the metadata when scraping, e.g. {@code de-DE}
 * @param artworkLanguage  preferred language of the artwork (ISO 639-1), e.g. {@code de}
 * @param videoExtensions  file extensions that the scan recognizes as a movie
 * @param excludedPaths    folders that the scan skips including their contents
 * @param proxy            proxy for requests to online services or {@code null} for a direct connection
 * @param tv               settings for TV shows; if omitted, same as for movies
 * @param mediaInfo        settings for ffprobe; if omitted, the defaults
 * @param rename           patterns and triggers for renaming; if omitted, the defaults
 * @param movieSets        storage of set artwork and identification when scraping; if omitted, the defaults
 */
public record KmmSettings(
        @NotBlank @Pattern(regexp = METADATA_LANGUAGE_PATTERN) String metadataLanguage,
        @NotBlank @Pattern(regexp = ARTWORK_LANGUAGE_PATTERN) String artworkLanguage,
        @NotNull @Size(min = 1, max = MAX_EXTENSIONS) List<@Pattern(regexp = EXTENSION_PATTERN) String> videoExtensions,
        @NotNull @Size(max = MAX_EXCLUDED_PATHS) List<@Size(max = MAX_PATH_LENGTH) String> excludedPaths,
        @Valid ProxySettings proxy,
        @Valid TvSettings tv,
        @Valid MediaInfoSettings mediaInfo,
        @Valid RenameSettings rename,
        @Valid MovieSetSettings movieSets) {

    public static final String METADATA_LANGUAGE_PATTERN = "[a-z]{2}(-[A-Z]{2})?";
    static final String ARTWORK_LANGUAGE_PATTERN = "[a-z]{2}";
    static final String EXTENSION_PATTERN = "[a-z0-9]{1,10}";
    static final int MAX_EXTENSIONS = 100;
    static final int MAX_EXCLUDED_PATHS = 500;
    static final int MAX_PATH_LENGTH = 1024;

    public static final KmmSettings DEFAULTS = new KmmSettings("de-DE", "de",
            ScanRules.DEFAULT_VIDEO_EXTENSIONS.stream().sorted().toList(), List.of(), null, null);

    private static final String EXTENSION_SEPARATOR = ".";

    public KmmSettings {
        metadataLanguage = strip(metadataLanguage);
        artworkLanguage = strip(artworkLanguage);
        videoExtensions = clean(videoExtensions).stream()
                .map(extension -> extension.startsWith(EXTENSION_SEPARATOR) ? extension.substring(1) : extension)
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .filter(extension -> !extension.isEmpty())
                .distinct()
                .toList();
        excludedPaths = clean(excludedPaths);
        tv = tv != null ? tv : TvSettings.like(metadataLanguage, artworkLanguage);
        mediaInfo = mediaInfo != null ? mediaInfo : MediaInfoSettings.DEFAULTS;
        rename = rename != null ? rename : RenameSettings.DEFAULTS;
        movieSets = movieSets != null ? movieSets : MovieSetSettings.DEFAULTS;
    }

    /** Settings without any values of their own for movie sets. */
    public KmmSettings(String metadataLanguage, String artworkLanguage, List<String> videoExtensions,
            List<String> excludedPaths, ProxySettings proxy, TvSettings tv, MediaInfoSettings mediaInfo,
            RenameSettings rename) {
        this(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv, mediaInfo, rename, null);
    }

    /** Settings without any values of their own for renaming. */
    public KmmSettings(String metadataLanguage, String artworkLanguage, List<String> videoExtensions,
            List<String> excludedPaths, ProxySettings proxy, TvSettings tv, MediaInfoSettings mediaInfo) {
        this(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv, mediaInfo, null, null);
    }

    /** Settings without any values of their own for ffprobe. */
    public KmmSettings(String metadataLanguage, String artworkLanguage, List<String> videoExtensions,
            List<String> excludedPaths, ProxySettings proxy, TvSettings tv) {
        this(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv, null, null, null);
    }

    /** Settings without any values of their own for TV shows; these follow the movie languages. */
    public KmmSettings(String metadataLanguage, String artworkLanguage, List<String> videoExtensions,
            List<String> excludedPaths, ProxySettings proxy) {
        this(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, null, null, null, null);
    }

    /** Whether the value is a language such as {@code de-DE}; values from foreign data need not be. */
    public static boolean isMetadataLanguage(String value) {
        return value != null && value.matches(METADATA_LANGUAGE_PATTERN);
    }

    KmmSettings withMetadataLanguage(String newMetadataLanguage) {
        return new KmmSettings(newMetadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv,
                mediaInfo, rename, movieSets);
    }

    KmmSettings withArtworkLanguage(String newArtworkLanguage) {
        return new KmmSettings(metadataLanguage, newArtworkLanguage, videoExtensions, excludedPaths, proxy, tv,
                mediaInfo, rename, movieSets);
    }

    KmmSettings withProxy(ProxySettings newProxy) {
        return new KmmSettings(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, newProxy, tv,
                mediaInfo, rename, movieSets);
    }

    public KmmSettings withTv(TvSettings newTv) {
        return new KmmSettings(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, newTv,
                mediaInfo, rename, movieSets);
    }

    public KmmSettings withMediaInfo(MediaInfoSettings newMediaInfo) {
        return new KmmSettings(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv,
                newMediaInfo, rename, movieSets);
    }

    public KmmSettings withRename(RenameSettings newRename) {
        return new KmmSettings(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv,
                mediaInfo, newRename, movieSets);
    }

    public KmmSettings withMovieSets(MovieSetSettings newMovieSets) {
        return new KmmSettings(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv,
                mediaInfo, rename, newMovieSets);
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }

    private static List<String> clean(List<String> values) {
        return values == null ? null : values.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
