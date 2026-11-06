package de.tiktak.kmm.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Reads and writes {@link KmmSettings} as key-value pairs. Values that are not stored
 * come from {@link KmmSettings#DEFAULTS}; lists are stored line by line in one value.
 */
@Component
@RequiredArgsConstructor
class SettingsStore {

    static final String METADATA_LANGUAGE = "metadata-language";
    static final String ARTWORK_LANGUAGE = "artwork-language";
    static final String VIDEO_EXTENSIONS = "video-extensions";
    static final String EXCLUDED_PATHS = "excluded-paths";
    static final String PROXY_HOST = "proxy-host";
    static final String PROXY_PORT = "proxy-port";
    static final String PROXY_USERNAME = "proxy-username";
    static final String PROXY_PASSWORD = "proxy-password";
    static final String TV_METADATA_LANGUAGE = "tv-metadata-language";
    static final String TV_ARTWORK_LANGUAGE = "tv-artwork-language";
    static final String TV_EPISODE_ORDERING = "tv-episode-ordering";
    static final String FFPROBE_PATH = "ffprobe-path";
    static final String PROBE_ON_SCRAPE = "probe-on-scrape";
    static final String PROBE_NEW_FILES = "probe-new-files";
    static final String RENAME_MOVIE_FOLDER = "rename-movie-folder";
    static final String RENAME_MOVIE_FILE = "rename-movie-file";
    static final String RENAME_TV_SHOW_FOLDER = "rename-tv-show-folder";
    static final String RENAME_SEASON_FOLDER = "rename-season-folder";
    static final String RENAME_EPISODE_FILE = "rename-episode-file";
    static final String RENAME_MOVIE_ON_EDIT = "rename-movie-on-edit";
    static final String RENAME_MOVIE_ON_SCRAPE = "rename-movie-on-scrape";
    static final String RENAME_TV_ON_EDIT = "rename-tv-on-edit";
    static final String RENAME_TV_ON_SCRAPE = "rename-tv-on-scrape";
    static final String RENAME_EPISODES_ON_SCAN = "rename-episodes-on-scan";
    static final String SET_FLAT_IMAGES = "set-flat-images";
    static final String SET_FLAT_IMAGES_FOLDER = "set-flat-images-folder";
    static final String SET_KODI_IMAGES = "set-kodi-images";
    static final String SET_KODI_IMAGES_FOLDER = "set-kodi-images-folder";
    static final String SET_ASSIGN_ON_SCRAPE = "set-assign-on-scrape";

    private static final String LINE_SEPARATOR = "\n";

    private final AppSettingRepository repository;

    KmmSettings load() {
        Map<String, String> values = repository.findAll().stream()
                .collect(Collectors.toMap(AppSetting::getKey, AppSetting::getValue));
        KmmSettings defaults = KmmSettings.DEFAULTS;
        String metadataLanguage = values.getOrDefault(METADATA_LANGUAGE, defaults.metadataLanguage());
        String artworkLanguage = values.getOrDefault(ARTWORK_LANGUAGE, defaults.artworkLanguage());
        return new KmmSettings(
                metadataLanguage,
                artworkLanguage,
                values.containsKey(VIDEO_EXTENSIONS) ? lines(values.get(VIDEO_EXTENSIONS)) : defaults.videoExtensions(),
                values.containsKey(EXCLUDED_PATHS) ? lines(values.get(EXCLUDED_PATHS)) : defaults.excludedPaths(),
                values.containsKey(PROXY_HOST) ? proxy(values) : null,
                new TvSettings(
                        values.getOrDefault(TV_METADATA_LANGUAGE, metadataLanguage),
                        values.getOrDefault(TV_ARTWORK_LANGUAGE, artworkLanguage),
                        EpisodeOrdering.valueOf(values.getOrDefault(TV_EPISODE_ORDERING, EpisodeOrdering.AIRED.name()))),
                mediaInfo(values),
                rename(values),
                movieSets(values));
    }

    private static MovieSetSettings movieSets(Map<String, String> values) {
        MovieSetSettings defaults = MovieSetSettings.DEFAULTS;
        return new MovieSetSettings(
                flag(values, SET_FLAT_IMAGES, defaults.flatImages()),
                values.getOrDefault(SET_FLAT_IMAGES_FOLDER, defaults.flatImagesFolder()),
                flag(values, SET_KODI_IMAGES, defaults.kodiImages()),
                values.getOrDefault(SET_KODI_IMAGES_FOLDER, defaults.kodiImagesFolder()),
                flag(values, SET_ASSIGN_ON_SCRAPE, defaults.assignOnScrape()));
    }

    private static MediaInfoSettings mediaInfo(Map<String, String> values) {
        MediaInfoSettings defaults = MediaInfoSettings.DEFAULTS;
        return new MediaInfoSettings(
                values.getOrDefault(FFPROBE_PATH, defaults.ffprobePath()),
                Boolean.parseBoolean(values.getOrDefault(PROBE_ON_SCRAPE, String.valueOf(defaults.probeOnScrape()))),
                Boolean.parseBoolean(values.getOrDefault(PROBE_NEW_FILES, String.valueOf(defaults.probeNewFiles()))));
    }

    private static RenameSettings rename(Map<String, String> values) {
        RenameSettings defaults = RenameSettings.DEFAULTS;
        return new RenameSettings(
                values.getOrDefault(RENAME_MOVIE_FOLDER, defaults.movieFolder()),
                values.getOrDefault(RENAME_MOVIE_FILE, defaults.movieFile()),
                values.getOrDefault(RENAME_TV_SHOW_FOLDER, defaults.tvShowFolder()),
                values.getOrDefault(RENAME_SEASON_FOLDER, defaults.seasonFolder()),
                values.getOrDefault(RENAME_EPISODE_FILE, defaults.episodeFile()),
                flag(values, RENAME_MOVIE_ON_EDIT, defaults.movieOnEdit()),
                flag(values, RENAME_MOVIE_ON_SCRAPE, defaults.movieOnScrape()),
                flag(values, RENAME_TV_ON_EDIT, defaults.tvOnEdit()),
                flag(values, RENAME_TV_ON_SCRAPE, defaults.tvOnScrape()),
                flag(values, RENAME_EPISODES_ON_SCAN, defaults.episodesOnScan()));
    }

    private static boolean flag(Map<String, String> values, String key, boolean defaultValue) {
        return Boolean.parseBoolean(values.getOrDefault(key, String.valueOf(defaultValue)));
    }

    void save(KmmSettings settings) {
        put(METADATA_LANGUAGE, settings.metadataLanguage());
        put(ARTWORK_LANGUAGE, settings.artworkLanguage());
        put(VIDEO_EXTENSIONS, String.join(LINE_SEPARATOR, settings.videoExtensions()));
        put(EXCLUDED_PATHS, String.join(LINE_SEPARATOR, settings.excludedPaths()));
        ProxySettings proxy = settings.proxy();
        put(PROXY_HOST, proxy == null ? null : proxy.host());
        put(PROXY_PORT, proxy == null ? null : String.valueOf(proxy.port()));
        put(PROXY_USERNAME, proxy == null ? null : proxy.username());
        put(PROXY_PASSWORD, proxy == null ? null : proxy.password());
        put(TV_METADATA_LANGUAGE, settings.tv().metadataLanguage());
        put(TV_ARTWORK_LANGUAGE, settings.tv().artworkLanguage());
        put(TV_EPISODE_ORDERING, settings.tv().episodeOrdering().name());
        put(FFPROBE_PATH, settings.mediaInfo().ffprobePath());
        put(PROBE_ON_SCRAPE, String.valueOf(settings.mediaInfo().probeOnScrape()));
        put(PROBE_NEW_FILES, String.valueOf(settings.mediaInfo().probeNewFiles()));
        RenameSettings rename = settings.rename();
        put(RENAME_MOVIE_FOLDER, rename.movieFolder());
        put(RENAME_MOVIE_FILE, rename.movieFile());
        put(RENAME_TV_SHOW_FOLDER, rename.tvShowFolder());
        put(RENAME_SEASON_FOLDER, rename.seasonFolder());
        put(RENAME_EPISODE_FILE, rename.episodeFile());
        put(RENAME_MOVIE_ON_EDIT, String.valueOf(rename.movieOnEdit()));
        put(RENAME_MOVIE_ON_SCRAPE, String.valueOf(rename.movieOnScrape()));
        put(RENAME_TV_ON_EDIT, String.valueOf(rename.tvOnEdit()));
        put(RENAME_TV_ON_SCRAPE, String.valueOf(rename.tvOnScrape()));
        put(RENAME_EPISODES_ON_SCAN, String.valueOf(rename.episodesOnScan()));
        MovieSetSettings movieSets = settings.movieSets();
        put(SET_FLAT_IMAGES, String.valueOf(movieSets.flatImages()));
        put(SET_FLAT_IMAGES_FOLDER, movieSets.flatImagesFolder());
        put(SET_KODI_IMAGES, String.valueOf(movieSets.kodiImages()));
        put(SET_KODI_IMAGES_FOLDER, movieSets.kodiImagesFolder());
        put(SET_ASSIGN_ON_SCRAPE, String.valueOf(movieSets.assignOnScrape()));
    }

    private void put(String key, String value) {
        if (value == null) {
            repository.deleteById(key);
            return;
        }
        repository.findById(key).ifPresentOrElse(
                setting -> setting.changeValue(value),
                () -> repository.save(new AppSetting(key, value)));
    }

    private static ProxySettings proxy(Map<String, String> values) {
        return new ProxySettings(values.get(PROXY_HOST), Integer.parseInt(values.get(PROXY_PORT)),
                values.get(PROXY_USERNAME), values.get(PROXY_PASSWORD));
    }

    private static List<String> lines(String value) {
        return value.isEmpty() ? List.of() : Arrays.asList(value.split(LINE_SEPARATOR));
    }
}
