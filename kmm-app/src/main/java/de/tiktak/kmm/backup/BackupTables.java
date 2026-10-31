package de.tiktak.kmm.backup;

import java.util.List;
import java.util.Map;

/**
 * Alle Tabellen der Sicherung. Tabellen stehen hinter denen, auf die sie verweisen, damit der Import beim Lesen der
 * Datei die neuen IDs ihrer Eltern schon kennt. Nur diese Namen gelangen in SQL, nie Namen aus einer Datei.
 */
final class BackupTables {

    static final String MEDIA_SOURCE = "media_source";
    static final String MOVIE_SET = "movie_set";
    static final String MOVIE = "movie";
    static final String TV_SHOW = "tv_show";
    static final String EPISODE = "episode";
    static final String KODI_HOST = "kodi_host";
    static final String KODI_HOST_PATH_MAPPING = "kodi_host_path_mapping";
    static final String SAVED_FILTER = "saved_filter";
    static final String APP_SETTING = "app_setting";
    static final String SETTING_KEY = "setting_key";
    static final String KODI_PASSWORD = "password";

    static final List<BackupTable> ALL = List.of(
            BackupTable.entity(MEDIA_SOURCE, List.of("path"), Map.of()),
            BackupTable.entity(MOVIE_SET, List.of("name"), Map.of()),
            BackupTable.entity(MOVIE, List.of("file_path_hash"),
                    Map.of("source_id", MEDIA_SOURCE, "movie_set_id", MOVIE_SET)),
            BackupTable.list("movie_genre", "movie_id", MOVIE),
            BackupTable.list("movie_credit", "movie_id", MOVIE),
            BackupTable.list("movie_rating", "movie_id", MOVIE),
            BackupTable.list("movie_studio", "movie_id", MOVIE),
            BackupTable.list("movie_country", "movie_id", MOVIE),
            BackupTable.list("movie_tag", "movie_id", MOVIE),
            BackupTable.entity(TV_SHOW, List.of("directory_path_hash"), Map.of("source_id", MEDIA_SOURCE)),
            BackupTable.list("tv_show_genre", "tv_show_id", TV_SHOW),
            BackupTable.list("tv_show_studio", "tv_show_id", TV_SHOW),
            BackupTable.list("tv_show_tag", "tv_show_id", TV_SHOW),
            BackupTable.list("tv_show_actor", "tv_show_id", TV_SHOW),
            BackupTable.list("tv_show_rating", "tv_show_id", TV_SHOW),
            BackupTable.list("tv_season", "tv_show_id", TV_SHOW),
            BackupTable.entity(EPISODE, List.of("file_path_hash", "season_number", "episode_number"),
                    Map.of("tv_show_id", TV_SHOW)),
            BackupTable.list("episode_credit", "episode_id", EPISODE),
            BackupTable.list("episode_rating", "episode_id", EPISODE),
            BackupTable.entity(KODI_HOST, List.of("name"), Map.of()),
            BackupTable.list(KODI_HOST_PATH_MAPPING, "kodi_host_id", KODI_HOST),
            BackupTable.entity(SAVED_FILTER, List.of("media_type", "name"), Map.of()),
            new BackupTable(APP_SETTING, null, List.of(SETTING_KEY), Map.of()));

    private BackupTables() {
    }
}
