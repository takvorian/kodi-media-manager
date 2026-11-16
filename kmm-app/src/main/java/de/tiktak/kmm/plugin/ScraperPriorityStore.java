package de.tiktak.kmm.plugin;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import de.tiktak.kmm.plugin.api.MediaType;
import lombok.RequiredArgsConstructor;

/**
 * Stores the scraper orders: those from the settings in the settings store, those of a source in
 * {@code media_source_scraper}, which goes when the source goes.
 */
@Repository
@RequiredArgsConstructor
class ScraperPriorityStore {

    private static final String SETTING_PREFIX = "scraper.priority.";

    private final JdbcClient jdbcClient;

    Optional<ScraperOrder> global(MediaType mediaType, ScraperKind kind) {
        return jdbcClient.sql("SELECT setting_value FROM app_setting WHERE setting_key = ?")
                .param(settingKey(mediaType, kind))
                .query(String.class)
                .optional()
                .map(ScraperOrder::parse);
    }

    void saveGlobal(MediaType mediaType, ScraperKind kind, ScraperOrder order) {
        String key = settingKey(mediaType, kind);
        jdbcClient.sql("DELETE FROM app_setting WHERE setting_key = ?").param(key).update();
        jdbcClient.sql("INSERT INTO app_setting (setting_key, setting_value) VALUES (?, ?)")
                .param(key).param(order.format()).update();
    }

    Map<ScraperKind, ScraperOrder> ofSource(long sourceId) {
        Map<ScraperKind, ScraperOrder> orders = new EnumMap<>(ScraperKind.class);
        jdbcClient.sql("SELECT kind, scraper_order FROM media_source_scraper WHERE source_id = ?")
                .param(sourceId)
                .query((row, number) -> Map.entry(row.getString("kind"), row.getString("scraper_order")))
                .list()
                .forEach(row -> kind(row.getKey())
                        .ifPresent(kind -> orders.put(kind, ScraperOrder.parse(row.getValue()))));
        return orders;
    }

    /** @param order the source's own order, empty to follow the one from the settings again */
    void saveForSource(long sourceId, ScraperKind kind, Optional<ScraperOrder> order) {
        jdbcClient.sql("DELETE FROM media_source_scraper WHERE source_id = ? AND kind = ?")
                .param(sourceId).param(kind.name()).update();
        order.ifPresent(own -> jdbcClient.sql(
                        "INSERT INTO media_source_scraper (source_id, kind, scraper_order) VALUES (?, ?, ?)")
                .param(sourceId).param(kind.name()).param(own.format()).update());
    }

    private static Optional<ScraperKind> kind(String name) {
        try {
            return Optional.of(ScraperKind.valueOf(name));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String settingKey(MediaType mediaType, ScraperKind kind) {
        return SETTING_PREFIX + mediaType.name() + "." + kind.name();
    }
}
