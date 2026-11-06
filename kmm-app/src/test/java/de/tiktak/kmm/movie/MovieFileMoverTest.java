package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.movie.MovieFileMover.Relocation;
import de.tiktak.kmm.movie.MovieRenamePlan.FileRename;
import de.tiktak.kmm.movie.MovieRenamePlan.Status;

class MovieFileMoverTest {

    @TempDir
    Path source;

    @Test
    void renamesFilesMovesTheFolderAndRemovesEmptyParents() throws IOException {
        givenFiles("alt/heat/heat.mkv", "alt/heat/heat.nfo", "alt/heat/extrafanart/1.jpg");

        Relocation relocation = MovieFileMover.move(plan("alt/heat", "H/Heat (1995)",
                new FileRename("heat.mkv", "Heat.mkv"), new FileRename("heat.nfo", "Heat.nfo")), source);

        assertThat(names("H/Heat (1995)")).containsExactly("Heat.mkv", "Heat.nfo", "extrafanart");
        assertThat(source.resolve("H/Heat (1995)/extrafanart/1.jpg")).exists();
        assertThat(source.resolve("alt")).doesNotExist();
        assertThat(source).exists();
        assertThat(relocation.changesDirectory()).isTrue();
        assertThat(relocation.relocate(source.resolve("alt/heat/heat.mkv")))
                .isEqualTo(source.resolve("H/Heat (1995)/Heat.mkv"));
        assertThat(relocation.relocate(source.resolve("alt/heat/extrafanart/1.jpg")))
                .isEqualTo(source.resolve("H/Heat (1995)/extrafanart/1.jpg"));
        assertThat(relocation.relocate(source.resolve("anderswo/x.srt"))).isEqualTo(source.resolve("anderswo/x.srt"));
    }

    @Test
    void changesOnlyTheCaseOfFolderAndFiles() throws IOException {
        givenFiles("heat/heat.mkv");

        Relocation relocation = MovieFileMover.move(plan("heat", "Heat", new FileRename("heat.mkv", "Heat.mkv")),
                source);

        assertThat(names("")).containsExactly("Heat");
        assertThat(names("Heat")).containsExactly("Heat.mkv");
        assertThat(relocation.directory()).isEqualTo(source.resolve("Heat"));
    }

    @Test
    void swapsNamesThroughTemporaryNames() throws IOException {
        givenFiles("heat/a.txt", "heat/b.txt");
        Files.writeString(source.resolve("heat/a.txt"), "A");

        MovieFileMover.move(plan("heat", "heat", new FileRename("a.txt", "b.txt"), new FileRename("b.txt", "a.txt")),
                source);

        assertThat(Files.readString(source.resolve("heat/b.txt"))).isEqualTo("A");
    }

    @Test
    void undoesEverythingWhenAStepFails() throws IOException {
        givenFiles("heat/heat.mkv", "heat/heat.nfo", "Heat (1995)/belegt.txt");

        assertThatThrownBy(() -> MovieFileMover.move(plan("heat", "Heat (1995)",
                new FileRename("heat.mkv", "Heat.mkv"), new FileRename("heat.nfo", "Heat.nfo")), source))
                .isInstanceOf(IOException.class);

        assertThat(names("heat")).containsExactly("heat.mkv", "heat.nfo");
        assertThat(names("Heat (1995)")).containsExactly("belegt.txt");
    }

    @Test
    void undoesTheFileRenamesWhenTheFolderIsTaken() throws IOException {
        givenFiles("heat/heat.mkv", "neu/Heat/belegt.txt");

        assertThatThrownBy(() -> MovieFileMover.move(plan("heat", "neu/Heat", new FileRename("heat.mkv", "x.mkv")),
                source)).isInstanceOf(IOException.class);

        assertThat(names("heat")).containsExactly("heat.mkv");
    }

    @Test
    void failsWhenAFileIsGone() throws IOException {
        givenFiles("heat/heat.mkv");

        assertThatThrownBy(() -> MovieFileMover.move(plan("heat", "H/Heat", new FileRename("heat.mkv", "Heat.mkv"),
                new FileRename("fehlt.mkv", "x.mkv")), source)).isInstanceOf(IOException.class);

        assertThat(names("heat")).containsExactly("heat.mkv");
        assertThat(source.resolve("H")).doesNotExist();
    }

    @Test
    void failsWhenTheFolderIsGone() {
        assertThatThrownBy(() -> MovieFileMover.move(plan("weg", "Weg"), source))
                .isInstanceOfSatisfying(LocalizedIOException.class, e -> assertThat(e.localizedText().key())
                        .isEqualTo("movie.rename.folderGone"));
    }

    @Test
    void relocatesFilesOfAFolderThatStays() {
        Relocation relocation = new Relocation(source, source, Map.of("heat.srt", "Heat.srt"));

        assertThat(relocation.changesDirectory()).isFalse();
        assertThat(relocation.relocate(source.resolve("heat.srt"))).isEqualTo(source.resolve("Heat.srt"));
        assertThat(relocation.relocate(source.resolve("other.srt"))).isEqualTo(source.resolve("other.srt"));
    }

    private MovieRenamePlan plan(String current, String target, FileRename... renames) {
        return new MovieRenamePlan(1, "Heat", source.resolve(current).toString(), source.resolve(target).toString(),
                List.of(renames), Status.RENAME, null);
    }

    private List<String> names(String directory) throws IOException {
        try (Stream<Path> files = Files.list(source.resolve(directory))) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    private void givenFiles(String... names) throws IOException {
        for (String name : names) {
            Path file = source.resolve(name);
            Files.createDirectories(file.getParent());
            Files.writeString(file, name);
        }
    }
}
