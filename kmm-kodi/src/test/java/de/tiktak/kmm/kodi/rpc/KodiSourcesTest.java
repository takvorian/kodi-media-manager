package de.tiktak.kmm.kodi.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class KodiSourcesTest {

    private final KodiSources sources = KodiSources.of(List.of("smb://yggdrasil/medien/filme/", "addons://sources/video/"));

    @Test
    void containsDirectoriesInOrBelowASourceWhateverTheirCase() {
        assertThat(sources.contain("smb://YGGDRASIL/medien/filme/")).isTrue();
        assertThat(sources.contain("smb://YGGDRASIL/medien/filme/S/Sliver (1993)/")).isTrue();
    }

    @Test
    void doesNotContainOtherPaths() {
        assertThat(sources.contain("a:\\medien\\filme\\S\\Sliver (1993)\\")).isFalse();
        assertThat(sources.contain("smb://yggdrasil/medien/")).isFalse();
        assertThat(sources.contain("smb://yggdrasil/medien/filme2/")).as("nur ähnlicher Name").isFalse();
    }

    @Test
    void comparesWindowsPathsWithoutRegardToSeparators() {
        KodiSources windows = KodiSources.of(List.of("D:\\Filme\\"));

        assertThat(windows.contain("d:\\filme\\Heat\\")).isTrue();
        assertThat(windows.contain("E:\\Filme\\Heat\\")).isFalse();
    }

    @Test
    void acceptsAMappingWiderThanASource() {
        assertThat(sources.overlap("smb://YGGDRASIL/medien")).isTrue();
        assertThat(sources.overlap("smb://yggdrasil/medien/filme/S/")).isTrue();
        assertThat(sources.overlap("a:\\medien\\filme")).isFalse();
    }

    @Test
    void readsCombinedSources() {
        KodiSources combined = KodiSources.of(List.of("multipath://smb%3a%2f%2fnas%2ffilme%2f/nfs%3a%2f%2fnas%2fmehr%2f/"));

        assertThat(combined.contain("smb://nas/filme/Heat/")).isTrue();
        assertThat(combined.contain("nfs://nas/mehr/Heat/")).isTrue();
        assertThat(combined.contain("smb://nas/anderes/")).isFalse();
    }

    @Test
    void ignoresAndHidesCredentials() {
        KodiSources secured = KodiSources.of(List.of("smb://kodi:geheim@nas/filme/"));

        assertThat(secured.contain("smb://nas/filme/Heat/")).isTrue();
        assertThat(secured.contain("smb://anderer:wert@nas/filme/Heat/")).isTrue();
        assertThat(secured).hasToString("smb://nas/filme/");
    }

    @Test
    void namesTheSourcesForAMessage() {
        assertThat(sources).hasToString("smb://yggdrasil/medien/filme/");
        assertThat(KodiSources.of(List.of())).hasToString("keine");
    }
}
