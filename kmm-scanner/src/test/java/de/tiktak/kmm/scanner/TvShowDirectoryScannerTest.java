package de.tiktak.kmm.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TvShowDirectoryScannerTest {

    @TempDir
    Path root;

    private TvShowDirectoryScanner scanner(ScanRules rules) {
        return new TvShowDirectoryScanner(rules, new MovieNameParser(), new EpisodeNameParser());
    }

    @Test
    void findsShowsWithEpisodesInAnyDepth() throws IOException {
        Path alf = Files.createDirectories(root.resolve("ALF (1986)"));
        create("ALF (1986)/Staffel 1/ALF - S01E02 - Der Fremde.avi");
        create("ALF (1986)/Staffel 1/ALF - S01E02 - Der Fremde.nfo");
        create("ALF (1986)/Staffel 1/ALF - S01E16E17 - Gestatten.avi");
        create("ALF (1986)/Specials/ALF - S00E01 - Making of.avi");
        create("ALF (1986)/ALF - Trailer.avi");
        create("ALF (1986)/extrafanart/fanart1.avi");
        create("Chernobyl (2019)/S01E01.1:23:45.mp4".replace(':', '_'));

        List<TvShowCandidate> shows = scanner(ScanRules.defaults()).scan(root);

        assertThat(shows).extracting(TvShowCandidate::title).containsExactly("ALF", "Chernobyl");
        TvShowCandidate first = shows.getFirst();
        assertThat(first.directory()).isEqualTo(alf);
        assertThat(first.year()).isEqualTo(1986);
        assertThat(first.episodes()).extracting(EpisodeCandidate::number).containsExactly(
                new EpisodeNumber(0, List.of(1)), new EpisodeNumber(1, List.of(2)), new EpisodeNumber(1, List.of(16, 17)));
        assertThat(first.episodes()).extracting(EpisodeCandidate::title)
                .containsExactly("Making of", "Der Fremde", "Gestatten");
        assertThat(first.unrecognizedFiles()).isEmpty();
    }

    @Test
    void reportsVideoFilesWithoutEpisodeNumber() throws IOException {
        create("Garfield und seine Freunde (1988)/Staffel 4/So4E46E47E48.avi");
        create("Garfield und seine Freunde (1988)/Staffel 4/Garfield - S04E45.avi");

        TvShowCandidate show = scanner(ScanRules.defaults()).scan(root).getFirst();

        assertThat(show.episodes()).hasSize(1);
        assertThat(show.unrecognizedFiles()).containsExactly(
                root.resolve("Garfield und seine Freunde (1988)/Staffel 4/So4E46E47E48.avi"));
    }

    @Test
    void skipsEmptyIgnoredAndExcludedFoldersAndFilesInTheSourceRoot() throws IOException {
        Files.createDirectories(root.resolve("Leer (2000)/Staffel 1"));
        create("extrafanart/Serie - S01E01.avi");
        create("Privat/Serie - S01E01.avi");
        create("Lose - S01E01.avi");
        ScanRules rules = new ScanRules(ScanRules.DEFAULT_VIDEO_EXTENSIONS, ScanRules.DEFAULT_IGNORED_DIRECTORIES,
                Set.of(root.resolve("Privat")));

        assertThat(scanner(rules).scan(root)).isEmpty();
        assertThat(scanner(rules).showDirectories(root)).containsExactly(root.resolve("Leer (2000)"));
        assertThat(scanner(rules).scanShow(root.resolve("Leer (2000)"))).isEmpty();
    }

    @Test
    void usesTheFolderNameWhenItHasNoParsableTitle() throws IOException {
        create("2001/S01E01.mkv");

        assertThat(scanner(ScanRules.defaults()).scan(root).getFirst().title()).isEqualTo("2001");
    }

    @Test
    void reportsAMissingSource() {
        assertThatThrownBy(() -> scanner(ScanRules.defaults()).scan(root.resolve("fehlt")))
                .isInstanceOf(UncheckedIOException.class);
    }

    private void create(String relativePath) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
    }
}
