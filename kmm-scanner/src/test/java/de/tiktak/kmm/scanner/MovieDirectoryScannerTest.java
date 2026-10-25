package de.tiktak.kmm.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MovieDirectoryScannerTest {

    private final MovieDirectoryScanner scanner = new MovieDirectoryScanner(ScanRules.defaults(), new MovieNameParser());

    @TempDir
    Path root;

    @Test
    void findsMovieFilesInSubdirectoriesWithTitleAndYear() throws IOException {
        Path heat = file("Heat (1995)/Heat (1995).mkv");
        Path matrix = file("The.Matrix.1999.1080p.BluRay.x264.mp4");

        List<MovieCandidate> candidates = scanner.scan(root);

        assertThat(candidates)
                .extracting(MovieCandidate::file, MovieCandidate::title, MovieCandidate::year,
                        MovieCandidate::format, MovieCandidate::partCount)
                .containsExactly(
                        tuple(heat, "Heat", 1995, DiscFormat.FILE, 1),
                        tuple(matrix, "The Matrix", 1999, DiscFormat.FILE, 1));
    }

    @Test
    void matchesVideoExtensionsCaseInsensitively() throws IOException {
        file("Heat.MKV");

        assertThat(scanner.scan(root)).hasSize(1);
    }

    @Test
    void ignoresNonVideoFilesAndFilesWithoutExtension() throws IOException {
        file("Heat/Heat.nfo");
        file("Heat/Heat-poster.jpg");
        file("Heat/Heat.srt");
        file("Heat/README");
        file("Heat/.mkv");

        assertThat(scanner.scan(root)).isEmpty();
    }

    @Test
    void ignoresSamplesTrailersAndExtrasDirectories() throws IOException {
        file("Heat/Heat.mkv");
        file("Heat/Heat-sample.mkv");
        file("Heat/Heat-trailer.mp4");
        file("Heat/trailer.mp4");
        file("Heat/Extras/Making of.mkv");
        file("Heat/Sample/heat.sample.mkv");
        file("Heat/Behind The Scenes/Interview.mkv");

        assertThat(scanner.scan(root)).extracting(MovieCandidate::title).containsExactly("Heat");
    }

    @Test
    void stacksMultiPartMoviesIntoOneCandidate() throws IOException {
        file("Heat/Heat (1995) cd2.avi");
        Path firstPart = file("Heat/Heat (1995) cd1.avi");
        file("Heat/Heat (1995) cd3.avi");

        List<MovieCandidate> candidates = scanner.scan(root);

        assertThat(candidates).singleElement().satisfies(candidate -> {
            assertThat(candidate.file()).isEqualTo(firstPart);
            assertThat(candidate.title()).isEqualTo("Heat");
            assertThat(candidate.year()).isEqualTo(1995);
            assertThat(candidate.partCount()).isEqualTo(3);
        });
    }

    @Test
    void keepsStacksOfDifferentMoviesApart() throws IOException {
        file("Heat-part1.mkv");
        file("Heat-part2.mkv");
        file("Ronin-part1.mkv");

        assertThat(scanner.scan(root))
                .extracting(MovieCandidate::title, MovieCandidate::partCount)
                .containsExactly(tuple("Heat", 2), tuple("Ronin", 1));
    }

    @Test
    void recognizesDvdFolderByParentDirectoryName() throws IOException {
        Path marker = file("Heat (1995)/VIDEO_TS/VIDEO_TS.IFO");
        file("Heat (1995)/VIDEO_TS/VTS_01_1.VOB");

        assertThat(scanner.scan(root)).singleElement().satisfies(candidate -> {
            assertThat(candidate.file()).isEqualTo(marker);
            assertThat(candidate.format()).isEqualTo(DiscFormat.DVD);
            assertThat(candidate.title()).isEqualTo("Heat");
            assertThat(candidate.year()).isEqualTo(1995);
        });
    }

    @Test
    void recognizesBluRayFolderAndSkipsItsStreams() throws IOException {
        Path marker = file("Ronin (1998)/bdmv/INDEX.BDMV");
        file("Ronin (1998)/bdmv/STREAM/00001.m2ts");

        assertThat(scanner.scan(root)).singleElement().satisfies(candidate -> {
            assertThat(candidate.file()).isEqualTo(marker);
            assertThat(candidate.format()).isEqualTo(DiscFormat.BLURAY);
            assertThat(candidate.title()).isEqualTo("Ronin");
        });
    }

    @Test
    void scansDiscDirectoryWithoutMarkerLikeNormalDirectory() throws IOException {
        file("Ronin/BDMV/STREAM/00001.m2ts");

        assertThat(scanner.scan(root)).singleElement()
                .satisfies(candidate -> assertThat(candidate.format()).isEqualTo(DiscFormat.FILE));
    }

    @Test
    void fallsBackToFolderNameWhenFileNameHasNoTitle() throws IOException {
        file("Heat (1995)/1080p.BluRay.mkv");

        assertThat(scanner.scan(root)).singleElement().satisfies(candidate -> {
            assertThat(candidate.title()).isEqualTo("Heat");
            assertThat(candidate.year()).isEqualTo(1995);
        });
    }

    @Test
    void usesRawNameWhenNeitherFileNorFolderHasTitle() throws IOException {
        file("1080p.BluRay.mkv");

        assertThat(scanner.scan(root)).singleElement()
                .satisfies(candidate -> assertThat(candidate.title()).isEqualTo("1080p.BluRay"));
    }

    @Test
    void returnsNothingForEmptyDirectory() {
        assertThat(scanner.scan(root)).isEmpty();
    }

    @Test
    void failsForMissingRoot() {
        assertThatExceptionOfType(UncheckedIOException.class).isThrownBy(() -> scanner.scan(root.resolve("missing")));
    }

    @Test
    void honoursCustomRules() throws IOException {
        file("Heat.mkv");
        file("Heat.xyz");
        var customScanner = new MovieDirectoryScanner(
                new ScanRules(Set.of("XYZ"), Set.of()), new MovieNameParser());

        assertThat(customScanner.scan(root)).extracting(candidate -> candidate.file().getFileName().toString())
                .containsExactly("Heat.xyz");
    }

    private Path file(String relativePath) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        return Files.createFile(file);
    }
}
