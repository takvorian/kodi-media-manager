package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.tvshow.TvShowFileMover.Relocation;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.EpisodeFile;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.FileRename;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.Status;

class TvShowFileMoverTest {

    @TempDir
    Path source;

    @Test
    void movesEpisodesIntoSeasonFoldersRenamesTheShowAndRemovesEmptyFolders() throws IOException {
        givenFiles("alt/lost/Season 1/e1.mkv", "alt/lost/Season 1/e1.nfo", "alt/lost/e2.mkv", "alt/lost/tvshow.nfo",
                "alt/lost/Season 9/rest.txt", "alt/lost/Season 9/e9.mkv");

        Relocation relocation = TvShowFileMover.move(plan("alt/lost", "Lost",
                file("Season 1", "Staffel 1", rename("e1.mkv", "Lost - S01E01.mkv"), rename("e1.nfo", "Lost - S01E01.nfo")),
                file("", "Staffel 1", rename("e2.mkv", "Lost - S01E02.mkv")),
                file("Season 9", "Staffel 9", rename("e9.mkv", "Lost - S09E01.mkv"))), source);

        assertThat(names("Lost")).containsExactly("Season 9", "Staffel 1", "Staffel 9", "tvshow.nfo");
        assertThat(names("Lost/Staffel 1")).containsExactly("Lost - S01E01.mkv", "Lost - S01E01.nfo", "Lost - S01E02.mkv");
        assertThat(names("Lost/Season 9")).as("not empty").containsExactly("rest.txt");
        assertThat(source.resolve("alt")).doesNotExist();
        assertThat(relocation.relocate(source.resolve("alt/lost/Season 1/e1.mkv")))
                .isEqualTo(source.resolve("Lost/Staffel 1/Lost - S01E01.mkv"));
        assertThat(relocation.relocate(source.resolve("alt/lost/tvshow.nfo"))).isEqualTo(source.resolve("Lost/tvshow.nfo"));
        assertThat(relocation.relocate(source.resolve("anderswo.srt"))).isEqualTo(source.resolve("anderswo.srt"));
    }

    @Test
    void leavesEpisodeFilesWithConflictsWhereTheyAre() throws IOException {
        givenFiles("Lost/e1.mkv", "Lost/e2.mkv");

        TvShowFileMover.move(plan("Lost", "Lost", file("", "Staffel 1", rename("e1.mkv", "x.mkv")),
                new EpisodeFile("S01E02", "", "Staffel 1", List.of(rename("e2.mkv", "x.mkv")), Status.CONFLICT,
                        LocalizedText.literal("belegt"))), source);

        assertThat(names("Lost")).containsExactly("Staffel 1", "e2.mkv");
    }

    @Test
    void undoesEverythingWhenAStepFails() throws IOException {
        givenFiles("lost/e1.mkv", "lost/e2.mkv", "Lost (2004)/belegt.txt");

        assertThatThrownBy(() -> TvShowFileMover.move(plan("lost", "Lost (2004)",
                file("", "Staffel 1", rename("e1.mkv", "Lost - S01E01.mkv")),
                file("", "Staffel 1", rename("e2.mkv", "Lost - S01E02.mkv"))), source))
                .isInstanceOf(IOException.class).hasMessageContaining("already exists");

        assertThat(names("lost")).containsExactly("e1.mkv", "e2.mkv");
    }

    @Test
    void failsWhenTheShowFolderIsGone() {
        assertThatThrownBy(() -> TvShowFileMover.move(plan("weg", "Weg"), source))
                .isInstanceOfSatisfying(LocalizedIOException.class, e -> assertThat(e.localizedText().key())
                        .isEqualTo("tvShow.rename.folderGone"));
    }

    private TvShowRenamePlan plan(String current, String target, EpisodeFile... files) {
        return new TvShowRenamePlan(4, "Lost", source.resolve(current).toString(), source.resolve(target).toString(),
                List.of(files), Status.RENAME, null);
    }

    private static EpisodeFile file(String currentFolder, String targetFolder, FileRename... renames) {
        return new EpisodeFile("S01E01", currentFolder, targetFolder, List.of(renames), Status.RENAME, null);
    }

    private static FileRename rename(String current, String target) {
        return new FileRename(current, target);
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
