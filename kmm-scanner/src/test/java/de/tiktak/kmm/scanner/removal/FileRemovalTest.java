package de.tiktak.kmm.scanner.removal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileRemovalTest {

    @TempDir
    Path source;

    @Test
    void listsAllFilesBelowAFolder() throws IOException {
        Path show = source.resolve("ALF");
        Path episode = file(show.resolve("Staffel 1/ALF S01E01.avi"));
        Path nfo = file(show.resolve("tvshow.nfo"));
        Files.createDirectories(show.resolve("leer"));

        assertThat(FileRemoval.listFiles(show)).containsExactly(episode, nfo);
    }

    @Test
    void reportsAFolderThatCannotBeRead() {
        assertThatThrownBy(() -> FileRemoval.listFiles(source.resolve("fehlt")))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void findsTheCompanionFilesOfAVideo() throws IOException {
        Path video = file(source.resolve("Heat.mkv"));
        Path part = file(source.resolve("Heat-cd2.mkv"));
        Path nfo = file(source.resolve("Heat.nfo"));
        Path poster = file(source.resolve("Heat-poster.jpg"));
        Path subtitle = file(source.resolve("Heat.de.srt"));
        file(source.resolve("Heat 2.mkv"));
        file(source.resolve("Heat 2.nfo"));
        file(source.resolve("Heater.mkv"));
        Files.createDirectories(source.resolve("Heat extras"));

        assertThat(FileRemoval.companions(source, "Heat", List.of("Heat 2", "Heater")))
                .containsExactlyInAnyOrder(subtitle, video, part, poster, nfo);
    }

    @Test
    void acceptsOnlyPathsBelowTheRoot() {
        assertThat(FileRemoval.isInside(source, source.resolve("a/b.mkv"))).isTrue();
        assertThat(FileRemoval.isInside(source, source)).isFalse();
        assertThat(FileRemoval.isInside(source, source.resolve("a/../.."))).isFalse();
        assertThat(FileRemoval.isInside(source.resolve("a"), source.resolve("ab/c.mkv"))).isFalse();
    }

    @Test
    void findsTheFoldersThatAreEmptiedUpToTheStop() throws IOException {
        Path season = source.resolve("ALF/Staffel 1");
        Path episode = file(season.resolve("S01E01.avi"));
        Path nfo = file(season.resolve("S01E01.nfo"));
        Files.createDirectories(season.resolve("leer"));
        file(source.resolve("ALF/tvshow.nfo"));

        assertThat(FileRemoval.emptiedDirectories(List.of(episode, nfo), source)).containsExactly(season);
        assertThat(FileRemoval.emptiedDirectories(List.of(episode), source)).isEmpty();
        assertThat(FileRemoval.emptiedDirectories(List.of(episode, nfo), season)).isEmpty();
    }

    @Test
    void deletesFilesAndTheFoldersEmptiedByIt() throws IOException {
        Path season = source.resolve("A/ALF/Staffel 1");
        Path episode = file(season.resolve("S01E01.avi"));
        Path nfo = file(season.resolve("S01E01.nfo"));
        Files.createDirectories(season.resolve("leer/tiefer"));
        Path other = file(source.resolve("B/other.avi"));

        Deletion deletion = FileRemoval.delete(List.of(episode, nfo, season.resolve("fehlt.srt")),
                List.of(season), source);

        assertThat(deletion.deleted()).containsExactly(episode, nfo);
        assertThat(deletion.failures()).isEmpty();
        assertThat(source.resolve("A")).doesNotExist();
        assertThat(other).exists();
        assertThat(source).exists();
    }

    @Test
    void keepsFoldersWithOtherFilesAndTheStop() throws IOException {
        Path show = source.resolve("ALF");
        Path episode = file(show.resolve("Staffel 1/S01E01.avi"));
        Path foreign = file(show.resolve("Staffel 1/notiz.txt"));

        Deletion deletion = FileRemoval.delete(List.of(episode), List.of(show.resolve("Staffel 1")), show);

        assertThat(deletion.deleted()).containsExactly(episode);
        assertThat(foreign).exists();

        FileRemoval.delete(List.of(foreign), List.of(), show);

        assertThat(show.resolve("Staffel 1")).doesNotExist();
        assertThat(show).exists();
    }

    @Test
    void reportsFilesThatCannotBeDeletedAndGoesOn() throws IOException {
        Path blocked = source.resolve("ordner.mkv");
        file(blocked.resolve("inhalt.txt"));
        Path episode = file(source.resolve("S01E01.avi"));

        Deletion deletion = FileRemoval.delete(List.of(blocked, episode), List.of(), source);

        assertThat(deletion.deleted()).containsExactly(episode);
        assertThat(deletion.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.key()).isEqualTo("removal.deleteFailed");
            assertThat(failure.args()).containsEntry("file", blocked.toString()).containsKey("reason");
        });
        assertThat(blocked).exists();
    }

    @Test
    void ignoresFoldersThatAreAlreadyGone() {
        Deletion deletion = FileRemoval.delete(List.of(), List.of(source.resolve("weg")), source);

        assertThat(deletion.deleted()).isEmpty();
        assertThat(deletion.failures()).isEmpty();
    }

    private static Path file(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }
}
