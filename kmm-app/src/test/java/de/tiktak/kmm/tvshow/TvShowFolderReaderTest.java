package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.scanner.EpisodeCandidate;
import de.tiktak.kmm.scanner.EpisodeNumber;
import de.tiktak.kmm.scanner.TvShowCandidate;
import de.tiktak.kmm.scanner.TvShowDirectoryScanner;

class TvShowFolderReaderTest {

    private final TvShowNfoFiles nfoFiles = new TvShowNfoFiles();
    private final TvShowFolderReader reader = new TvShowFolderReader(nfoFiles);
    private final TvShowDirectoryScanner scanner = mock(TvShowDirectoryScanner.class);

    @TempDir
    Path directory;

    @Test
    void readsAllNfosOfANewShow() throws IOException {
        Path show = givenShow("<tvshow><title>ALF</title></tvshow>");
        Path first = givenEpisode(show, "S01E01", "<episodedetails><title>Eins</title></episodedetails>");
        Path second = givenEpisode(show, "S01E02", "kaputt");
        Path third = givenEpisode(show, "S01E03", null);

        ScannedTvShow scanned = reader.read(scanner, show, KnownNfos.NONE).orElseThrow();

        assertThat(scanned.showNfo()).isPresent();
        assertThat(scanned.loadedShowNfo(scanned.showNfo().get())).get()
                .satisfies(loaded -> assertThat(loaded.content().orElseThrow().title()).isEqualTo("ALF"));
        assertThat(scanned.episodeNfos()).containsOnlyKeys(first, second);
        assertThat(scanned.loadedEpisodeNfo(first, scanned.episodeNfos().get(first))).get()
                .satisfies(loaded -> assertThat(loaded.content().orElseThrow()).singleElement()
                        .satisfies(nfo -> assertThat(nfo.title()).isEqualTo("Eins")));
        assertThat(scanned.loadedEpisodeNfo(second, scanned.episodeNfos().get(second))).get()
                .satisfies(loaded -> assertThat(loaded.content()).isEmpty());
        assertThat(scanned.loadedEpisodeNfos()).doesNotContainKey(third);
    }

    @Test
    void skipsNfosThatAreUnchangedSinceTheLastScan() throws IOException {
        Path show = givenShow("<tvshow><title>ALF</title></tvshow>");
        Path unchanged = givenEpisode(show, "S01E01", "<episodedetails><title>Eins</title></episodedetails>");
        Path changed = givenEpisode(show, "S01E02", "<episodedetails><title>Zwei</title></episodedetails>");
        KnownNfos known = new KnownNfos(Map.of(show.toString(), modifiedAt(show.resolve("tvshow.nfo"))),
                Map.of(unchanged.toString(), Set.of(modifiedAt(nfoOf(unchanged))),
                        changed.toString(), Set.of(Instant.EPOCH)));

        ScannedTvShow scanned = reader.read(scanner, show, known).orElseThrow();

        assertThat(scanned.loadedShowNfo()).isEmpty();
        assertThat(scanned.episodeNfos()).containsOnlyKeys(unchanged, changed);
        assertThat(scanned.loadedEpisodeNfos()).containsOnlyKeys(changed);
    }

    @Test
    void ignoresFoldersWithoutEpisodes() {
        Path empty = directory.resolve("Leer");
        when(scanner.scanShow(empty)).thenReturn(Optional.empty());

        assertThat(reader.read(scanner, empty, KnownNfos.NONE)).isEmpty();
    }

    private Path givenShow(String nfo) throws IOException {
        Path show = Files.createDirectories(directory.resolve("ALF"));
        Files.writeString(show.resolve("tvshow.nfo"), nfo);
        when(scanner.scanShow(show)).thenAnswer(invocation -> Optional.of(new TvShowCandidate(show, "ALF", null, null,
                episodesIn(show), List.of())));
        return show;
    }

    private Path givenEpisode(Path show, String name, String nfo) throws IOException {
        Path file = Files.createFile(show.resolve(name + ".avi"));
        if (nfo != null) {
            Files.writeString(nfoOf(file), nfo);
        }
        return file;
    }

    private static List<EpisodeCandidate> episodesIn(Path show) throws IOException {
        try (var files = Files.list(show)) {
            return files.filter(file -> file.toString().endsWith(".avi")).sorted()
                    .map(file -> new EpisodeCandidate(file, new EpisodeNumber(1, List.of(1)), null))
                    .toList();
        }
    }

    private static Path nfoOf(Path episodeFile) {
        return episodeFile.resolveSibling(episodeFile.getFileName().toString().replace(".avi", ".nfo"));
    }

    private static Instant modifiedAt(Path nfo) throws IOException {
        return new TvNfoState(nfo, Files.getLastModifiedTime(nfo).toInstant()).modifiedAt();
    }
}
