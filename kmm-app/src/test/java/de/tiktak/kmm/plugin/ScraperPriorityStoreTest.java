package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import de.tiktak.kmm.plugin.ScraperOrder.Entry;
import de.tiktak.kmm.plugin.api.MediaType;

@DataJpaTest
class ScraperPriorityStoreTest {

    private static final ScraperOrder ORDER = new ScraperOrder(List.of(new Entry("tmdb", true),
            new Entry("tvdb", false)));

    @Autowired
    private DataSource dataSource;

    private JdbcClient jdbcClient;
    private ScraperPriorityStore store;
    private long sourceId;

    @BeforeEach
    void setUp() {
        jdbcClient = JdbcClient.create(dataSource);
        store = new ScraperPriorityStore(jdbcClient);
        jdbcClient.sql("""
                INSERT INTO media_source (name, path, media_type, created_at)
                VALUES ('Serien', '/medien/serien', 'TV_SHOW', CURRENT_TIMESTAMP)""").update();
        sourceId = jdbcClient.sql("SELECT id FROM media_source WHERE name = 'Serien'").query(Long.class).single();
    }

    @Test
    void keepsTheOrdersFromTheSettingsPerMediaTypeAndKind() {
        assertThat(store.global(MediaType.TV_SHOW, ScraperKind.METADATA)).isEmpty();

        store.saveGlobal(MediaType.TV_SHOW, ScraperKind.METADATA, ORDER);
        store.saveGlobal(MediaType.TV_SHOW, ScraperKind.METADATA, ORDER);

        assertThat(store.global(MediaType.TV_SHOW, ScraperKind.METADATA)).contains(ORDER);
        assertThat(store.global(MediaType.MOVIE, ScraperKind.METADATA)).isEmpty();
        assertThat(jdbcClient.sql("SELECT setting_value FROM app_setting WHERE setting_key = ?")
                .param("scraper.priority.TV_SHOW.METADATA").query(String.class).single()).isEqualTo("tmdb,-tvdb");
    }

    @Test
    void keepsTheOrdersOfASourceUntilItFollowsTheSettingsAgain() {
        store.saveForSource(sourceId, ScraperKind.METADATA, Optional.of(ORDER));
        store.saveForSource(sourceId, ScraperKind.ARTWORK, Optional.of(ORDER));
        store.saveForSource(sourceId, ScraperKind.ARTWORK, Optional.empty());

        assertThat(store.ofSource(sourceId)).containsOnlyKeys(ScraperKind.METADATA)
                .containsEntry(ScraperKind.METADATA, ORDER);
    }

    @Test
    void forgetsTheOrdersWithTheSource() {
        store.saveForSource(sourceId, ScraperKind.THEME, Optional.of(ORDER));

        jdbcClient.sql("DELETE FROM media_source WHERE id = ?").param(sourceId).update();

        assertThat(store.ofSource(sourceId)).isEmpty();
    }

    @Test
    void readsAndWritesTheStoredForm() {
        assertThat(ScraperOrder.parse(" tmdb , -fanarttv,,-")).isEqualTo(new ScraperOrder(List.of(
                new Entry("tmdb", true), new Entry("fanarttv", false))));
        assertThat(ORDER.format()).isEqualTo("tmdb,-tvdb");
    }
}
