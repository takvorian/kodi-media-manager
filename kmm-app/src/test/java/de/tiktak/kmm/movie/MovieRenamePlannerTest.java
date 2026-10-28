package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.movie.MovieRenamePlan.FileRename;
import de.tiktak.kmm.movie.MovieRenamePlan.Status;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;

class MovieRenamePlannerTest {

    private static final Map<String, String> HEAT = Map.of("title", "Heat", "year", "1995", "source", "bluray");

    @TempDir
    Path source;

    @Test
    void renamesTheFolderInPlaceAndAllFilesOfTheMovie() throws IOException {
        Path video = givenFiles("H/heat 1995/heat.mkv", "H/heat 1995/heat.nfo", "H/heat 1995/heat-poster.jpg",
                "H/heat 1995/heat.de.forced.srt", "H/heat 1995/heat_trailer.mp4", "H/heat 1995/heatwave.txt",
                "H/heat 1995/poster.jpg");

        MovieRenamePlan plan = plan(video, DiscFormat.FILE, 1, true, Set.of(), "{title}[ ({year})]",
                "{title}[.{source}]");

        assertThat(plan.status()).isEqualTo(Status.RENAME);
        assertThat(plan.currentDirectory()).isEqualTo(source.resolve("H/heat 1995").toString());
        assertThat(plan.targetDirectory()).isEqualTo(source.resolve("H/Heat (1995)").toString());
        assertThat(plan.renames()).containsExactly(
                new FileRename("heat-poster.jpg", "Heat.bluray-poster.jpg"),
                new FileRename("heat.de.forced.srt", "Heat.bluray.de.forced.srt"),
                new FileRename("heat.mkv", "Heat.bluray.mkv"),
                new FileRename("heat.nfo", "Heat.bluray.nfo"),
                new FileRename("heat_trailer.mp4", "Heat.bluray_trailer.mp4"));
        assertThat(plan.message()).isNull();
    }

    @Test
    void placesFoldersRelativeToTheSourceWhenThePatternHasFolders() throws IOException {
        Path video = givenFiles("alt/Heat/Heat.mkv");

        MovieRenamePlan plan = plan(video, DiscFormat.FILE, 1, true, Set.of(), "{title:first}/{title} ({year})",
                "{title}");

        assertThat(plan.targetDirectory()).isEqualTo(source.resolve("H/Heat (1995)").toString());
        assertThat(plan.renames()).isEmpty();
        assertThat(plan.status()).isEqualTo(Status.RENAME);
    }

    @Test
    void renamesAllPartsOfAStackedMovie() throws IOException {
        Path first = givenFiles("Heat/Heat cd1.avi", "Heat/Heat cd2.avi", "Heat/Heat.nfo");

        MovieRenamePlan plan = plan(first, DiscFormat.FILE, 2, true, Set.of(), "Heat", "{title} ({year})");

        assertThat(plan.renames()).containsExactly(new FileRename("Heat cd1.avi", "Heat (1995) cd1.avi"),
                new FileRename("Heat cd2.avi", "Heat (1995) cd2.avi"),
                new FileRename("Heat.nfo", "Heat (1995).nfo"));
    }

    @Test
    void keepsASharedFolderAndTheFilesOfOtherMovies() throws IOException {
        Path video = givenFiles("Sammlung/Heat.mkv", "Sammlung/Heat.nfo", "Sammlung/Heat 2.mkv", "Sammlung/Heat 2.nfo");

        MovieRenamePlan plan = plan(video, DiscFormat.FILE, 1, false, Set.of("Heat 2"), "{title} ({year})",
                "{title} ({year})");

        assertThat(plan.targetDirectory()).isEqualTo(plan.currentDirectory());
        assertThat(plan.renames()).containsExactly(new FileRename("Heat.mkv", "Heat (1995).mkv"),
                new FileRename("Heat.nfo", "Heat (1995).nfo"));
    }

    @Test
    void renamesOnlyTheFolderOfADisc() throws IOException {
        Path ifo = givenFiles("heat/VIDEO_TS/VIDEO_TS.IFO", "heat/poster.jpg");

        MovieRenamePlan plan = plan(ifo, DiscFormat.DVD, 1, true, Set.of(), "{title} ({year})", "{title}");

        assertThat(plan.targetDirectory()).isEqualTo(source.resolve("Heat (1995)").toString());
        assertThat(plan.renames()).isEmpty();
    }

    @Test
    void reportsNamesThatAreAlreadyRight() throws IOException {
        Path video = givenFiles("Heat (1995)/Heat.mkv");

        assertThat(plan(video, DiscFormat.FILE, 1, true, Set.of(), "{title} ({year})", "{title}").status())
                .isEqualTo(Status.UNCHANGED);
    }

    @Test
    void reportsAnOccupiedFolderOrFileAsConflict() throws IOException {
        Path video = givenFiles("heat/heat.mkv", "Heat (1995)/other.mkv");
        MovieRenamePlan folder = plan(video, DiscFormat.FILE, 1, true, Set.of(), "{title} ({year})", "{title}");
        assertThat(folder.status()).isEqualTo(Status.CONFLICT);
        assertThat(folder.message()).contains("existiert bereits");

        givenFiles("heat/heat2.mkv");
        MovieRenamePlan file = plan(video, DiscFormat.FILE, 1, false, Set.of(), "{title}", "heat2");
        assertThat(file.status()).isEqualTo(Status.CONFLICT);
        assertThat(file.message()).isEqualTo("Die Datei „heat2.mkv“ existiert bereits.");
    }

    @Test
    void onlyChangingTheCaseIsNoConflict() throws IOException {
        Path video = givenFiles("heat/heat.mkv");

        MovieRenamePlan plan = plan(video, DiscFormat.FILE, 1, true, Set.of(), "{title}", "{title}");

        assertThat(plan.status()).isEqualTo(Status.RENAME);
        assertThat(plan.targetDirectory()).isEqualTo(source.resolve("Heat").toString());
        assertThat(plan.renames()).containsExactly(new FileRename("heat.mkv", "Heat.mkv"));
    }

    @Test
    void failsWhenAPatternYieldsNoName() throws IOException {
        Path video = givenFiles("Heat/Heat.mkv");

        MovieRenamePlan folder = plan(video, DiscFormat.FILE, 1, true, Set.of(), "{edition}", "{title}");
        MovieRenamePlan file = plan(video, DiscFormat.FILE, 1, true, Set.of(), "{title}", "[{edition}]");

        assertThat(folder.status()).isEqualTo(Status.FAILED);
        assertThat(folder.message()).isEqualTo("Das Ordnermuster ergibt keinen Namen.");
        assertThat(file.status()).isEqualTo(Status.FAILED);
        assertThat(file.message()).isEqualTo("Das Dateimuster ergibt keinen gültigen Dateinamen.");
    }

    @Test
    void failsWhenTheFolderCannotBeRead() {
        Path video = source.resolve("fehlt/Heat.mkv");

        MovieRenamePlan plan = plan(video, DiscFormat.FILE, 1, false, Set.of(), "{title}", "{title} x");

        assertThat(plan.status()).isEqualTo(Status.FAILED);
        assertThat(plan.message()).startsWith("Der Ordner ist nicht lesbar");
    }

    private MovieRenamePlan plan(Path mainFile, DiscFormat format, int parts, boolean ownsFolder,
                                 Set<String> otherBaseNames, String folderPattern, String filePattern) {
        MovieRenamePlanner.Candidate candidate = new MovieRenamePlanner.Candidate(7, "Heat", mainFile, format, parts,
                source, ownsFolder, otherBaseNames, HEAT::get);
        return MovieRenamePlanner.plan(candidate, NamePattern.parse(folderPattern, NamePlaceholders.MOVIE),
                NamePattern.parse(filePattern, NamePlaceholders.MOVIE));
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
