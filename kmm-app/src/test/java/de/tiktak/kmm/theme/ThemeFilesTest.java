package de.tiktak.kmm.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeCandidate;
import de.tiktak.kmm.plugin.api.ThemeScraper;

class ThemeFilesTest {

    private static final URI AUDIO = URI.create("http://televisiontunes.com/uploads/audio/Alf.mp3");
    private static final ThemeCandidate CANDIDATE = new ThemeCandidate("Main Theme", "ALF", AUDIO, AUDIO, AUDIO, null);

    private final ThemeFiles files = new ThemeFiles();
    private final ThemeScraper scraper = mock(ThemeScraper.class);

    @TempDir
    Path directory;

    private ThemeTarget target;

    @BeforeEach
    void setUp() {
        target = new ThemeTarget(MediaType.TV_SHOW, "ALF", null, 1986, directory);
    }

    @Test
    void describesAMissingAndAnExistingTheme() throws IOException {
        assertThat(files.info(target)).isEqualTo(new ThemeInfo(false, directory.resolve("theme.mp3").toString(),
                null, null));
        assertThat(files.find(target)).isEmpty();

        Files.write(directory.resolve("theme.mp3"), new byte[] {1, 2, 3});

        assertThat(files.info(target)).satisfies(info -> {
            assertThat(info.exists()).isTrue();
            assertThat(info.sizeBytes()).isEqualTo(3);
            assertThat(info.modifiedAt()).isNotNull();
        });
        assertThat(files.find(target)).contains(directory.resolve("theme.mp3"));
    }

    @Test
    void replacesTheThemeWithTheDownloadAndLeavesNoTemporaryFile() throws IOException {
        Files.writeString(directory.resolve("theme.mp3"), "alt");
        doAnswer(invocation -> Files.writeString(invocation.getArgument(1), "neu")).when(scraper)
                .download(any(), any());

        files.save(target, scraper, CANDIDATE);

        assertThat(Files.readString(directory.resolve("theme.mp3"))).isEqualTo("neu");
        try (var entries = Files.list(directory)) {
            assertThat(entries).containsExactly(directory.resolve("theme.mp3"));
        }
    }

    @Test
    void keepsTheOldThemeWhenTheDownloadFails() throws IOException {
        Files.writeString(directory.resolve("theme.mp3"), "alt");
        doAnswer(invocation -> {
            Files.writeString(invocation.getArgument(1), "halb");
            throw new ScraperException("abgebrochen");
        }).when(scraper).download(any(), any());

        assertThatThrownBy(() -> files.save(target, scraper, CANDIDATE)).isInstanceOf(ScraperException.class);

        assertThat(Files.readString(directory.resolve("theme.mp3"))).isEqualTo("alt");
        try (var entries = Files.list(directory)) {
            assertThat(entries).containsExactly(directory.resolve("theme.mp3"));
        }
    }

    @Test
    void deletesThemesAndReportsWhatCannotBeDeleted() throws IOException {
        Files.writeString(directory.resolve("theme.mp3"), "alt");
        files.delete(target);
        files.delete(target);
        assertThat(directory.resolve("theme.mp3")).doesNotExist();

        Path notEmpty = Files.createDirectories(directory.resolve("ordner/theme.mp3")).getParent();
        Files.writeString(notEmpty.resolve("theme.mp3/inhalt"), "x");
        assertThatThrownBy(() -> files.delete(new ThemeTarget(MediaType.MOVIE, "Heat", null, 1995, notEmpty)))
                .isInstanceOfSatisfying(ThemeProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT));
    }
}
