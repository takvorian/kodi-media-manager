package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.scanner.MovieCandidate;
import de.tiktak.kmm.scanner.MovieNameParser;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;

class MovieScannerFactoryTest {

    @TempDir
    Path library;

    @Test
    void createsScannerWithExtensionsAndExcludedPathsOfTheSettings() throws IOException {
        createFile("Heat (1995)/Heat.mkv");
        createFile("Ronin (1998)/Ronin.xyz");
        createFile("Privat/Urlaub.xyz");
        SettingsService settingsService = mock(SettingsService.class);
        when(settingsService.current()).thenReturn(new KmmSettings("de-DE", "de", List.of("xyz"),
                List.of(library.resolve("Privat").toString()), null));

        List<MovieCandidate> candidates = new MovieScannerFactory(settingsService, new MovieNameParser()).create()
                .scan(library);

        assertThat(candidates).extracting(MovieCandidate::title).containsExactly("Ronin");
    }

    private void createFile(String relativePath) throws IOException {
        Path file = library.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }
}
