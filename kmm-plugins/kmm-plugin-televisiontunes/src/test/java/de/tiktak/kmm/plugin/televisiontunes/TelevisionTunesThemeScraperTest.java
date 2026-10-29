package de.tiktak.kmm.plugin.televisiontunes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeCandidate;

class TelevisionTunesThemeScraperTest {

    private static final String SEARCH_ALF = """
            <div class="song-grid">
            <div class="song-item-wrap" data-show="ALF" data-has-img="1"><a class="song-item" href="/show/ALF">
              <div class="song-info"><div class="song-title">ALF</div></div></a></div>
            <div class="song-item-wrap" data-show="Alfred J. Kwak" data-has-img="1"><a class="song-item"
              href="/show/Alfred%20J.%20Kwak"><div class="song-title">Alfred J. Kwak</div></a></div>
            <a href="/Alf.html">ALF - Main Theme</a>
            </div>""";
    private static final String SHOW_ALF = """
            <h1>ALF (2 Theme Songs)</h1>
            <div class="thumb-wrap" onclick="playInBar('/uploads/audio/Alf.mp3','ALF','Main Theme','/Alf_2.html',
              this.dataset.img)"></div>
            <a onclick="playInBar('/uploads/audio/Alf.mp3','ALF','Main Theme','/Alf_2.html','x')">Play</a>
            <div onclick="playInBar('/uploads/audio/Alf - Season 3 and 4.mp3','ALF','Season 3 and 4',
              '/Alf_-_Season_3_and_4.html','x')"></div>
            <div onclick="playInBar('/elsewhere/evil.mp3','ALF','Fremd','/x.html','x')"></div>""";
    private static final String SEARCH_HEAT = """
            <div class="song-item-wrap" data-show="Heat Guy J"><a class="song-item" href="/show/Heat%20Guy%20J">x</a>
            </div>
            <div class="song-item-wrap" data-show="Cooper&#039;s Half Hour"><a class="song-item"
              href="/show/Cooper's%20Half%20Hour">x</a></div>""";
    private static final String SHOW_COOPER = """
            <div onclick="playInBar('/uploads/audio/Coopers.mp3','Cooper\\'s Half Hour','Opening','/Coopers.html','x')">
            </div>""";
    private static final byte[] MP3 = {'I', 'D', '3', 4, 0, 0};

    private FakeTelevisionTunesServer site;
    private TelevisionTunesThemeScraper scraper;

    @TempDir
    Path directory;

    @BeforeEach
    void setUp() {
        site = new FakeTelevisionTunesServer()
                .page("/search.php?q=ALF", SEARCH_ALF)
                .page("/show/ALF", SHOW_ALF)
                .page("/search.php?q=Heat", SEARCH_HEAT)
                .page("/show/Heat%20Guy%20J", "")
                .page("/show/Cooper's%20Half%20Hour", SHOW_COOPER)
                .file("/uploads/audio/Alf.mp3", "audio/mpeg", MP3)
                .file("/uploads/audio/Page.mp3", "text/html", MP3);
        scraper = new TelevisionTunesThemeScraper();
        scraper.initialize(new TestPluginContext().with("base-url", site.baseUrl() + "/"));
    }

    @AfterEach
    void tearDown() {
        site.close();
    }

    @Test
    void findsTheThemesOfTheShowWithTheSameName() {
        var themes = scraper.findThemes(request(MediaType.TV_SHOW, " ALF "));

        assertThat(themes).containsExactly(
                new ThemeCandidate("Main Theme", "ALF", url("/uploads/audio/Alf.mp3"), url("/uploads/audio/Alf.mp3"),
                        url("/Alf_2.html"), null),
                new ThemeCandidate("Season 3 and 4", "ALF", url("/uploads/audio/Alf%20-%20Season%203%20and%204.mp3"),
                        url("/uploads/audio/Alf%20-%20Season%203%20and%204.mp3"), url("/Alf_-_Season_3_and_4.html"),
                        null));
        assertThat(site.requests()).containsExactly("/search.php?q=ALF", "/show/ALF");
    }

    @Test
    void loadsTheFirstResultsWhenNoNameMatches() {
        var themes = scraper.findThemes(request(MediaType.MOVIE, "Heat"));

        assertThat(themes).singleElement().satisfies(theme -> {
            assertThat(theme.subject()).isEqualTo("Cooper's Half Hour");
            assertThat(theme.name()).isEqualTo("Opening");
        });
        assertThat(site.requests()).contains("/show/Heat%20Guy%20J", "/show/Cooper's%20Half%20Hour");
    }

    @Test
    void returnsNothingForOtherMediaOrUnknownTitles() {
        assertThat(scraper.findThemes(request(MediaType.TV_EPISODE, "ALF"))).isEmpty();
        assertThat(scraper.findThemes(request(MediaType.TV_SHOW, "Unbekannt"))).isEmpty();
        assertThat(scraper.supportedMediaTypes()).containsExactlyInAnyOrder(MediaType.MOVIE, MediaType.TV_SHOW);
    }

    @Test
    void downloadsTheMp3() throws IOException {
        Path target = directory.resolve("theme.mp3");
        Files.writeString(target, "alt");

        scraper.download(candidate(url("/uploads/audio/Alf.mp3")), target);

        assertThat(Files.readAllBytes(target)).isEqualTo(MP3);
    }

    @Test
    void refusesForeignAddressesAndNonAudioAnswers() {
        Path target = directory.resolve("theme.mp3");

        assertThatThrownBy(() -> scraper.download(candidate(URI.create("http://example.org/uploads/audio/x.mp3")),
                target)).isInstanceOf(ScraperException.class);
        assertThatThrownBy(() -> scraper.download(candidate(url("/uploads/audio/../secret.mp3")), target))
                .isInstanceOf(ScraperException.class);
        assertThatThrownBy(() -> scraper.download(candidate(url("/uploads/audio/Page.mp3")), target))
                .isInstanceOf(ScraperException.class).hasMessageContaining("keine Audiodatei");
        assertThatThrownBy(() -> scraper.download(candidate(url("/uploads/audio/Fehlt.mp3")), target))
                .isInstanceOf(ScraperException.class);
        assertThat(target).doesNotExist();
    }

    @Test
    void reportsAnUnreachableSite() {
        TelevisionTunesThemeScraper offline = new TelevisionTunesThemeScraper();
        offline.initialize(new TestPluginContext().with("base-url", "http://127.0.0.1:1"));

        assertThatThrownBy(() -> offline.findThemes(request(MediaType.TV_SHOW, "ALF")))
                .isInstanceOf(ScraperException.class).hasMessageContaining("nicht erreichbar");
        assertThatThrownBy(() -> new TelevisionTunesThemeScraper().findThemes(request(MediaType.TV_SHOW, "ALF")))
                .isInstanceOf(IllegalStateException.class);
    }

    private URI url(String path) {
        return URI.create(site.baseUrl() + path);
    }

    private ThemeCandidate candidate(URI source) {
        return new ThemeCandidate("Main Theme", "ALF", source, source, url("/Alf_2.html"), null);
    }

    private static ScrapeRequest request(MediaType type, String title) {
        return new ScrapeRequest(type, title, null, Map.of());
    }
}
