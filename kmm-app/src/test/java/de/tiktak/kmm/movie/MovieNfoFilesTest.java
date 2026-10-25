package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.nfo.NfoException;
import de.tiktak.kmm.nfo.NfoReader;
import de.tiktak.kmm.nfo.NfoWriter;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;

class MovieNfoFilesTest {

    private final NfoReader reader = new NfoReader();
    private final MovieNfoFiles nfoFiles = new MovieNfoFiles(reader, new NfoWriter());

    @TempDir
    Path directory;

    @Test
    void findsNothingWithoutNfo() {
        assertThat(nfoFiles.find(companionFiles())).isEmpty();
    }

    @Test
    void prefersFileNameNfoOverFolderNfo() throws IOException {
        Files.writeString(directory.resolve("movie.nfo"), "<movie/>");
        Path fileNfo = Files.writeString(directory.resolve("Heat.nfo"), "<movie/>");

        assertThat(nfoFiles.find(companionFiles())).hasValueSatisfying(state -> {
            assertThat(state.file()).isEqualTo(fileNfo);
            assertThat(state.modifiedAt()).isNotNull();
        });
    }

    @Test
    void findsFolderNfo() throws IOException {
        Path folderNfo = Files.writeString(directory.resolve("movie.nfo"), "<movie/>");

        assertThat(nfoFiles.find(companionFiles())).hasValueSatisfying(
                state -> assertThat(state.file()).isEqualTo(folderNfo));
    }

    @Test
    void createsNfoNamedAfterMovieFile() {
        NfoFileState written = nfoFiles.write(companionFiles(), metadata("tt0113277"), null);

        assertThat(written.file()).isEqualTo(directory.resolve("Heat.nfo"));
        assertThat(reader.read(written.file()).title()).isEqualTo("Heat");
        assertThat(nfoFiles.read(written).uniqueId("imdb")).contains("tt0113277");
    }

    @Test
    void updatesExistingFolderNfoInPlace() throws IOException {
        Path folderNfo = Files.writeString(directory.resolve("movie.nfo"),
                "<movie><title>Alt</title><uniqueid type=\"tvdb\">42</uniqueid><director>Michael Mann</director></movie>");

        NfoFileState written = nfoFiles.write(companionFiles(), metadata("tt0113277"), null);

        assertThat(written.file()).isEqualTo(folderNfo);
        assertThat(directory.resolve("Heat.nfo")).doesNotExist();
        String content = Files.readString(folderNfo);
        assertThat(content).contains("<title>Heat</title>", "<director>Michael Mann</director>", ">42</uniqueid>");
    }

    @Test
    void replacesUnreadableExistingNfo() throws IOException {
        Files.writeString(directory.resolve("Heat.nfo"), "<movie><title>kaputt</movie>");

        NfoFileState written = nfoFiles.write(companionFiles(), metadata(null), null);

        assertThat(reader.read(written.file()).title()).isEqualTo("Heat");
    }

    @Test
    void reportsUnwritableLocation() {
        CompanionFiles missingDirectory = CompanionFiles.of(
                directory.resolve("fehlt").resolve("Heat.mkv"), DiscFormat.FILE, 1);

        assertThatThrownBy(() -> nfoFiles.write(missingDirectory, metadata(null), null)).isInstanceOf(NfoException.class);
    }

    private CompanionFiles companionFiles() {
        return CompanionFiles.of(directory.resolve("Heat.mkv"), DiscFormat.FILE, 1);
    }

    private static MovieMetadata metadata(String imdbId) {
        return new MovieMetadata("Heat", null, null, 1995, null, "Handlung", null, null, 170, null,
                List.of("Krimi"), imdbId, null);
    }
}
