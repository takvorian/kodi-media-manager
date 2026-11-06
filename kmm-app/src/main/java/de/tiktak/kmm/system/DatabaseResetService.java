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
 * Clears the database. Files such as media, NFOs and images are left untouched. While a job is running,
 * nothing is deleted, and no job starts while clearing is in progress.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseResetService {

    /** Dependent tables come before the ones they reference. */
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
     * @param keepSettings whether settings and API keys are kept
     * @throws de.tiktak.kmm.job.JobAlreadyRunningException if a job is currently running
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
