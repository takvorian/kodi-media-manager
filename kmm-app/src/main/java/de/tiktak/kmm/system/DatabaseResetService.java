package de.tiktak.kmm.system;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.settings.ApiKeyService;
import de.tiktak.kmm.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Leert die Datenbank. Dateien wie Medien, NFOs und Bilder bleiben unberührt. Solange ein Job läuft, wird
 * nichts gelöscht, und während des Leerens startet keiner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseResetService {

    /** Abhängige Tabellen stehen vor denen, auf die sie verweisen. */
    static final List<String> LIBRARY_TABLES = List.of(
            "episode_credit", "episode_rating", "episode", "tv_season",
            "tv_show_genre", "tv_show_studio", "tv_show_tag", "tv_show_actor", "tv_show_rating", "tv_show",
            "movie_genre", "movie_credit", "movie_rating", "movie_studio", "movie_country", "movie_tag", "movie",
            "movie_set", "media_source", "kodi_host_path_mapping", "kodi_host", "saved_filter");
    static final String SETTINGS_TABLE = "app_setting";

    private final JobService jobService;
    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactions;
    private final SettingsService settingsService;
    private final ApiKeyService apiKeyService;

    /**
     * @param keepSettings ob Einstellungen und API-Keys erhalten bleiben
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException wenn gerade ein Job läuft
     */
    public void clear(boolean keepSettings) {
        jobService.runWhileIdle(() -> {
            transactions.executeWithoutResult(status -> tables(keepSettings)
                    .forEach(table -> jdbcClient.sql("DELETE FROM " + table).update()));
            if (!keepSettings) {
                settingsService.reload();
                apiKeyService.reload();
            }
        });
        log.warn("Database cleared, settings {}", keepSettings ? "kept" : "deleted");
    }

    private static Stream<String> tables(boolean keepSettings) {
        return keepSettings ? LIBRARY_TABLES.stream() : Stream.concat(LIBRARY_TABLES.stream(), Stream.of(SETTINGS_TABLE));
    }
}
