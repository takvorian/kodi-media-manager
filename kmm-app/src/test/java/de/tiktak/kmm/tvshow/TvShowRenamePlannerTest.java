package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.EpisodeFile;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.FileRename;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.Status;

class TvShowRenamePlannerTest {

    private static final TvShowMetadata LOST = new TvShowMetadata("Lost", null, null, 2004, null, null, null, null,
            null, List.of(), List.of(), List.of(), "73739", null, null, null);

    @TempDir
    Path source;

    @Test
    void movesEpisodesWithTheirCompanionsIntoSeasonFoldersAndRenamesTheShowFolder() throws IOException {
        Path pilot = givenFiles("lost/lost.s01e01.mkv", "lost/lost.s01e01.nfo", "lost/lost.s01e01-thumb.jpg",
                "lost/lost.s01e01.de.srt", "lost/lost.s01e010.mkv", "lost/tvshow.nfo", "lost/season01-poster.jpg");

        TvShowRenamePlan plan = plan(true, "{show} ({year})", "Staffel {season}",
                file(pilot, episode(1, 1, "Pilot")));

        assertThat(plan.status()).isEqualTo(Status.RENAME);
        assertThat(plan.targetDirectory()).isEqualTo(source.resolve("Lost (2004)").toString());
        assertThat(plan.files()).containsExactly(new EpisodeFile("S01E01", "", "Staffel 1", List.of(
                new FileRename("lost.s01e01-thumb.jpg", "Lost - S01E01 - Pilot-thumb.jpg"),
                new FileRename("lost.s01e01.de.srt", "Lost - S01E01 - Pilot.de.srt"),
                new FileRename("lost.s01e01.mkv", "Lost - S01E01 - Pilot.mkv"),
                new FileRename("lost.s01e01.nfo", "Lost - S01E01 - Pilot.nfo")), Status.RENAME, null));
        assertThat(plan.hasChanges()).isTrue();
    }

    @Test
    void namesMultiEpisodeFilesAfterAllTheirEpisodes() throws IOException {
        Path file = givenFiles("Lost/Season 1/lost.mkv");

        TvShowRenamePlan plan = plan(false, "{show}", "Season {season:2}",
                file(file, episode(1, 1, "Pilot (1)"), episode(1, 2, "Pilot (2)")));

        EpisodeFile renamed = plan.files().getFirst();
        assertThat(renamed.episodes()).isEqualTo("S01E01E02");
        assertThat(renamed.currentFolder()).isEqualTo("Season 1");
        assertThat(renamed.targetFolder()).isEqualTo("Season 01");
        assertThat(renamed.renames()).containsExactly(
                new FileRename("lost.mkv", "Lost - S01E01E02 - Pilot (1) - Pilot (2).mkv"));
        assertThat(plan.targetDirectory()).as("Serie ist die Quelle").isEqualTo(plan.currentDirectory());
    }

    @Test
    void keepsEpisodesInTheShowFolderWhenTheSeasonPatternYieldsNothing() throws IOException {
        Path file = givenFiles("Lost/Staffel 1/Lost - S01E01 - Pilot.mkv");

        TvShowRenamePlan plan = plan(true, "{show}", "[{imdb}]", file(file, episode(1, 1, "Pilot")));

        assertThat(plan.files()).singleElement().satisfies(moved -> {
            assertThat(moved.targetFolder()).isEmpty();
            assertThat(moved.renames()).containsExactly(
                    new FileRename("Lost - S01E01 - Pilot.mkv", "Lost - S01E01 - Pilot.mkv"));
        });
    }

    @Test
    void reportsShowsThatAreNamedRightAlready() throws IOException {
        Path file = givenFiles("Lost/Staffel 1/Lost - S01E01 - Pilot.mkv");

        TvShowRenamePlan plan = plan(true, "{show}", "Staffel {season}", file(file, episode(1, 1, "Pilot")));

        assertThat(plan.status()).isEqualTo(Status.UNCHANGED);
        assertThat(plan.files()).isEmpty();
        assertThat(plan.hasChanges()).isFalse();
    }

    @Test
    void skipsTheWholeShowWhenItsNewFolderIsTaken() throws IOException {
        Path file = givenFiles("lost/x.mkv", "Lost (2004)/belegt.txt");

        TvShowRenamePlan plan = plan(true, "{show} ({year})", "Staffel {season}", file(file, episode(1, 1, "Pilot")));

        assertThat(plan.status()).isEqualTo(Status.CONFLICT);
        assertThat(plan.message()).contains("existiert bereits");
    }

    @Test
    void skipsEpisodeFilesWhoseNamesAreTaken() throws IOException {
        Path first = givenFiles("Lost/a.mkv", "Lost/b.mkv", "Lost/Staffel 1/Lost - S01E03 - Drei.mkv");
        Path second = source.resolve("Lost/b.mkv");
        Path third = givenFiles("Lost/c.mkv");

        TvShowRenamePlan plan = plan(true, "{show}", "Staffel {season}",
                file(first, episode(1, 1, "Eins")), file(second, episode(1, 1, "Eins")),
                file(third, episode(1, 3, "Drei")));

        assertThat(plan.files()).extracting(EpisodeFile::status)
                .containsExactly(Status.CONFLICT, Status.CONFLICT, Status.CONFLICT);
        assertThat(plan.files()).extracting(EpisodeFile::message).containsExactly(
                "Mehrere Episodendateien bekämen denselben Namen.", "Mehrere Episodendateien bekämen denselben Namen.",
                "Die Datei „Lost - S01E03 - Drei.mkv“ existiert bereits.");
        assertThat(plan.status()).isEqualTo(Status.CONFLICT);
        assertThat(plan.message()).isEqualTo("Keine Episodendatei lässt sich umbenennen.");
    }

    @Test
    void reportsPatternsWithoutNamesAndUnreadableFolders() throws IOException {
        Path file = givenFiles("Lost/x.mkv");

        assertThat(plan(true, "[{imdb}]", "Staffel {season}", file(file, episode(1, 1, "x"))).message())
                .isEqualTo("Das Muster für Serienordner ergibt keinen Namen.");
        TvShowRenamePlan noFileName = plan(false, "{show}", "Staffel {season}", "[{imdb}]",
                file(file, episode(1, 1, "x")));
        assertThat(noFileName.files()).singleElement().extracting(EpisodeFile::status).isEqualTo(Status.FAILED);
        TvShowRenamePlan unreadable = plan(false, "{show}", "Staffel {season}",
                file(source.resolve("Lost/weg/x.mkv"), episode(1, 1, "x")));
        assertThat(unreadable.status()).isEqualTo(Status.FAILED);
        assertThat(unreadable.message()).startsWith("Ein Ordner der Serie ist nicht lesbar");
    }

    private TvShowRenamePlan plan(boolean ownsFolder, String showFolder, String seasonFolder,
                                  TvShowRenamePlanner.CandidateFile... files) {
        return plan(ownsFolder, showFolder, seasonFolder, "{show} - S{season:2}E{episode:2}[ - {title}]", files);
    }

    private TvShowRenamePlan plan(boolean ownsFolder, String showFolder, String seasonFolder, String episodeFile,
                                  TvShowRenamePlanner.CandidateFile... files) {
        Path showDirectory = source.resolve(source.relativize(files[0].file()).getName(0));
        TvShowRenamePlanner.Candidate candidate = new TvShowRenamePlanner.Candidate(4, "Lost", showDirectory,
                ownsFolder ? source : showDirectory, ownsFolder,
                new TvNameValues(LOST, TvShowExtras.EMPTY, List.of()), List.of(files));
        return TvShowRenamePlanner.plan(candidate, new TvShowRenamePlanner.Patterns(
                NamePattern.parse(showFolder, NamePlaceholders.TV_SHOW),
                NamePattern.parse(seasonFolder, NamePlaceholders.SEASON_FOLDER),
                NamePattern.parse(episodeFile, NamePlaceholders.EPISODE_FILE)));
    }

    private static TvShowRenamePlanner.CandidateFile file(Path file, TvNameValues.EpisodeValues... episodes) {
        return new TvShowRenamePlanner.CandidateFile(file, List.of(episodes));
    }

    private static TvNameValues.EpisodeValues episode(int season, int episode, String title) {
        return new TvNameValues.EpisodeValues(season, episode, title, null, null, StreamDetails.EMPTY);
    }

    private Path givenFiles(String... names) throws IOException {
        Path first = null;
        for (String name : names) {
            Path file = source.resolve(name);
            Files.createDirectories(file.getParent());
            Files.writeString(file, name);
            first = first == null ? file : first;
        }
        return first;
    }
}
