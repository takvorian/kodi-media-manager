package de.tiktak.kmm.removal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.scanner.removal.Deletion;

class RemovalFilesTest {

    private static final List<LocalizedText> ENTRIES = List.of(LocalizedText.literal("Heat (1995)"));

    @TempDir
    Path temp;

    @Test
    void plansTheFilesWithTheirCompanionsAndTheEmptiedFolders() throws IOException {
        Path source = temp.resolve("filme");
        Path video = file(source.resolve("Heat/Heat.mkv"));
        Path nfo = file(source.resolve("Heat/Heat.nfo"));
        file(source.resolve("Heat 2/Heat 2.mkv"));
        RemovalFiles files = new RemovalFiles();

        files.scope(source, "Filme", source).addWithCompanions(video, List.of());
        RemovalPlan plan = files.plan(ENTRIES);

        assertThat(plan.entries()).isEqualTo(ENTRIES);
        assertThat(plan.files()).containsExactly(video.toString(), nfo.toString());
        assertThat(plan.directories()).containsExactly(source.resolve("Heat").toString());
        assertThat(plan.notes()).isEmpty();
        assertThat(plan.canDeleteFiles()).isTrue();
        assertThat(files.contains(nfo)).isTrue();
        assertThat(files.contains(source.resolve("Heat 2/Heat 2.mkv"))).isFalse();
    }

    @Test
    void deletesThePlannedFilesAndKeepsTheSource() throws IOException {
        Path source = temp.resolve("filme");
        Path video = file(source.resolve("H/Heat/Heat.mkv"));
        Path nfo = file(source.resolve("H/Heat/Heat.nfo"));
        Path other = file(source.resolve("Ronin/Ronin.mkv"));
        RemovalFiles files = new RemovalFiles();
        files.scope(source, "Filme", source).addWithCompanions(video, List.of());

        Deletion deletion = files.delete();

        assertThat(deletion.deleted()).containsExactlyInAnyOrder(video, nfo);
        assertThat(deletion.failures()).isEmpty();
        assertThat(source.resolve("H")).doesNotExist();
        assertThat(other).exists();
        assertThat(source).exists();
    }

    @Test
    void findsAFolderEmptiedByTheFilesOfSeveralMovies() throws IOException {
        Path source = temp.resolve("filme");
        Path heat = file(source.resolve("Sammlung/Heat.mkv"));
        Path ronin = file(source.resolve("Sammlung/Ronin.mkv"));
        RemovalFiles files = new RemovalFiles();

        files.scope(source, "Filme", source).addWithCompanions(heat, List.of("Ronin"));
        files.scope(source, "Filme", source).addWithCompanions(ronin, List.of("Heat"));

        assertThat(files.plan(ENTRIES).directories()).containsExactly(source.resolve("Sammlung").toString());
    }

    @Test
    void acceptsFilesDirectlyInTheSource() throws IOException {
        Path source = temp.resolve("filme");
        Path video = file(source.resolve("Heat.mkv"));
        Path nfo = file(source.resolve("Heat.nfo"));
        Path other = file(source.resolve("Ronin.mkv"));
        RemovalFiles files = new RemovalFiles();
        files.scope(source, "Filme", source).addWithCompanions(video, List.of("Ronin"));

        RemovalPlan plan = files.plan(ENTRIES);
        files.delete();

        assertThat(plan.files()).containsExactly(video.toString(), nfo.toString());
        assertThat(plan.directories()).isEmpty();
        assertThat(plan.canDeleteFiles()).isTrue();
        assertThat(video).doesNotExist();
        assertThat(other).exists();
    }

    @Test
    void notesMissingFilesWithoutBlocking() {
        Path source = temp.resolve("filme");
        RemovalFiles files = new RemovalFiles();

        files.scope(source, "Filme", source).addWithCompanions(source.resolve("weg/Heat.mkv"), List.of());
        RemovalPlan plan = files.plan(ENTRIES);

        assertThat(plan.files()).isEmpty();
        assertThat(plan.canDeleteFiles()).isTrue();
        assertThat(plan.notes()).singleElement().satisfies(note -> {
            assertThat(note.key()).isEqualTo("removal.note.missingFiles");
            assertThat(note.args()).containsEntry("count", 1).containsEntry("files", "Heat.mkv");
        });
    }

    @Test
    void refusesFilesOutsideTheSource() throws IOException {
        Path source = Files.createDirectories(temp.resolve("filme"));
        Path outside = file(temp.resolve("anderswo/Heat.mkv"));
        RemovalFiles files = new RemovalFiles();

        files.scope(source, "Filme", source).addWithCompanions(outside, List.of());
        RemovalPlan plan = files.plan(ENTRIES);

        assertThat(plan.files()).isEmpty();
        assertThat(plan.canDeleteFiles()).isFalse();
        assertThat(plan.notes()).singleElement().satisfies(note -> {
            assertThat(note.key()).isEqualTo("removal.note.outsideSource");
            assertThat(note.args()).containsEntry("source", "Filme");
        });
        assertThatThrownBy(files::delete).isInstanceOfSatisfying(RemovalProblemException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(outside).exists();
    }

    @Test
    void neverDeletesTheSourceItself() throws IOException {
        Path source = temp.resolve("serien");
        Path episode = file(source.resolve("S01E01.avi"));
        RemovalFiles files = new RemovalFiles();

        List<Path> content = files.scope(source, "Serien", source).addDirectory(source);

        assertThat(content).isEmpty();
        assertThat(files.canDelete()).isFalse();
        assertThat(files.plan(ENTRIES).notes()).extracting(LocalizedText::key)
                .containsExactly("removal.note.sourceItself");
        assertThatThrownBy(files::delete).isInstanceOf(RemovalProblemException.class);
        assertThat(episode).exists();
    }

    @Test
    void addsAWholeFolder() throws IOException {
        Path source = temp.resolve("serien");
        Path episode = file(source.resolve("ALF/Staffel 1/S01E01.avi"));
        Path nfo = file(source.resolve("ALF/tvshow.nfo"));
        RemovalFiles files = new RemovalFiles();

        List<Path> content = files.scope(source, "Serien", source).addDirectory(source.resolve("ALF"));
        RemovalPlan plan = files.plan(List.of());

        assertThat(content).containsExactly(episode, nfo);
        assertThat(plan.files()).containsExactly(episode.toString(), nfo.toString());
        assertThat(plan.directories()).containsExactly(source.resolve("ALF").toString(),
                source.resolve("ALF/Staffel 1").toString());
        files.delete();
        assertThat(source.resolve("ALF")).doesNotExist();
        assertThat(source).exists();
    }

    @Test
    void notesAMissingFolder() {
        Path source = temp.resolve("serien");
        RemovalFiles files = new RemovalFiles();

        assertThat(files.scope(source, "Serien", source).addDirectory(source.resolve("ALF"))).isEmpty();

        assertThat(files.plan(List.of()).notes()).extracting(LocalizedText::key)
                .containsExactly("removal.note.missingFiles");
    }

    @Test
    void blocksDeletingWhenAFolderCannotBeRead() throws IOException {
        Path source = temp.resolve("filme");
        Path notAFolder = file(source.resolve("Heat"));
        RemovalFiles files = new RemovalFiles();

        files.scope(source, "Filme", source).addCompanions(notAFolder, "Heat", List.of());
        files.scope(source, "Filme", source).addDirectory(notAFolder.resolve("Unterordner"));

        assertThat(files.canDelete()).isFalse();
        assertThat(files.plan(List.of()).notes()).extracting(LocalizedText::key)
                .contains("removal.note.unreadable");
    }

    @Test
    void blocksForAGivenReason() {
        RemovalFiles files = new RemovalFiles();
        LocalizedText reason = LocalizedText.of("removal.note.sharedDiscFolder").with("folder", "x");

        files.block(reason);

        assertThat(files.plan(List.of()).notes()).containsExactly(reason);
        assertThat(files.canDelete()).isFalse();
    }

    @Test
    void listsAtMostTenFileNames() {
        List<Path> paths = IntStream.rangeClosed(1, 12).mapToObj(number -> temp.resolve("f" + number + ".txt"))
                .toList();

        assertThat(RemovalFiles.listing(paths.subList(0, 2))).isEqualTo("f1.txt, f2.txt");
        assertThat(RemovalFiles.listing(paths)).startsWith("f1.txt, f2.txt").endsWith("f10.txt, …");
    }

    private static Path file(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }
}
