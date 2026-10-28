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
 * Benutzereinstellungen von KMM. Listen werden bereinigt (getrimmt, ohne Leereinträge und Duplikate),
 * Dateiendungen ohne Punkt und klein geschrieben.
 *
 * @param metadataLanguage Sprache der Metadaten beim Scrapen, z. B. {@code de-DE}
 * @param artworkLanguage  bevorzugte Sprache der Bilder (ISO 639-1), z. B. {@code de}
 * @param videoExtensions  Dateiendungen, die der Scan als Film erkennt
 * @param excludedPaths    Ordner, die der Scan samt Inhalt überspringt
 * @param proxy            Proxy für Anfragen an Online-Dienste oder {@code null} für eine direkte Verbindung
 * @param tv               Einstellungen für Serien; ohne Angabe wie bei Filmen
 * @param mediaInfo        Einstellungen für ffprobe; ohne Angabe die Vorgaben
 * @param rename           Muster und Auslöser für das Umbenennen; ohne Angabe die Vorgaben
 * @param movieSets        Ablage der Set-Bilder und Zuordnung beim Scrapen; ohne Angabe die Vorgaben
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

    static final String METADATA_LANGUAGE_PATTERN = "[a-z]{2}(-[A-Z]{2})?";
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

    /** Einstellungen ohne eigene Angaben zu Filmsets. */
    public KmmSettings(String metadataLanguage, String artworkLanguage, List<String> videoExtensions,
            List<String> excludedPaths, ProxySettings proxy, TvSettings tv, MediaInfoSettings mediaInfo,
            RenameSettings rename) {
        this(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv, mediaInfo, rename, null);
    }

    /** Einstellungen ohne eigene Angaben zum Umbenennen. */
    public KmmSettings(String metadataLanguage, String artworkLanguage, List<String> videoExtensions,
            List<String> excludedPaths, ProxySettings proxy, TvSettings tv, MediaInfoSettings mediaInfo) {
        this(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv, mediaInfo, null, null);
    }

    /** Einstellungen ohne eigene Angaben für ffprobe. */
    public KmmSettings(String metadataLanguage, String artworkLanguage, List<String> videoExtensions,
            List<String> excludedPaths, ProxySettings proxy, TvSettings tv) {
        this(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, tv, null, null, null);
    }

    /** Einstellungen ohne eigene Angaben für Serien; diese folgen den Film-Sprachen. */
    public KmmSettings(String metadataLanguage, String artworkLanguage, List<String> videoExtensions,
            List<String> excludedPaths, ProxySettings proxy) {
        this(metadataLanguage, artworkLanguage, videoExtensions, excludedPaths, proxy, null, null, null, null);
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
