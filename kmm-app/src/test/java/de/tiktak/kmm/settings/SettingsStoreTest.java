package de.tiktak.kmm.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(SettingsStore.class)
class SettingsStoreTest {

    @Autowired
    private SettingsStore store;

    @Autowired
    private AppSettingRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void returnsDefaultsWhenNothingIsStored() {
        assertThat(store.load()).isEqualTo(KmmSettings.DEFAULTS);
    }

    @Test
    void savesAndLoadsAllValues() {
        var settings = new KmmSettings("en-US", "en", List.of("mkv", "iso"), List.of("/media/privat", "/media/tmp"),
                new ProxySettings("proxy.local", 3128, "user", "secret"), new TvSettings("de-DE", "de",
                EpisodeOrdering.DVD), new MediaInfoSettings("/opt/ffprobe", false, true),
                new RenameSettings("{title}", "{title} ({year})", "{show:sort}", "Staffel {season}",
                        "{show} {season}x{episode:2}", true, true, false, true, true));

        store.save(settings);
        entityManager.flush();
        entityManager.clear();

        assertThat(store.load()).isEqualTo(settings);
    }

    @Test
    void letsTvLanguagesFollowTheMovieLanguagesUntilTheyAreSaved() {
        repository.save(new AppSetting(SettingsStore.METADATA_LANGUAGE, "en-US"));
        entityManager.flush();

        assertThat(store.load().tv()).isEqualTo(new TvSettings("en-US", "de", EpisodeOrdering.AIRED));
    }

    @Test
    void storesEmptyListsAndRemovesProxy() {
        store.save(new KmmSettings("de-DE", "de", List.of("mkv"), List.of(),
                new ProxySettings("proxy.local", 3128, null, null)));
        entityManager.flush();

        store.save(new KmmSettings("de-DE", "de", List.of("mkv"), List.of(), null));
        entityManager.flush();
        entityManager.clear();

        KmmSettings loaded = store.load();
        assertThat(loaded.excludedPaths()).isEmpty();
        assertThat(loaded.proxy()).isNull();
        assertThat(repository.existsById(SettingsStore.PROXY_HOST)).isFalse();
    }
}
