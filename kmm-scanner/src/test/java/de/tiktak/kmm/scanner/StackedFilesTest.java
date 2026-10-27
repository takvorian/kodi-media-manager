package de.tiktak.kmm.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StackedFilesTest {

    @TempDir
    Path directory;

    @Test
    void countsExistingPartsOfTheSameStack() throws IOException {
        Path first = Files.createFile(directory.resolve("Heat CD1.avi"));
        Files.createFile(directory.resolve("Heat CD2.avi"));
        Files.createFile(directory.resolve("Heat CD3.avi"));
        Files.createFile(directory.resolve("Ronin CD1.avi"));
        Files.createFile(directory.resolve("Heat CD2.mkv"));

        assertThat(StackedFiles.partCount(first)).isEqualTo(3);
    }

    @Test
    void listsThePartsOfAStackInTheOrderOfTheirNumbers() throws IOException {
        Path second = Files.createFile(directory.resolve("Heat cd2.avi"));
        Path first = Files.createFile(directory.resolve("Heat cd1.avi"));
        Files.createFile(directory.resolve("Ronin cd1.avi"));
        Path single = Files.createFile(directory.resolve("Ronin.mkv"));

        assertThat(StackedFiles.parts(second)).containsExactly(first, second);
        assertThat(StackedFiles.parts(single)).containsExactly(single);
        assertThatThrownBy(() -> StackedFiles.parts(directory.resolve("fehlt/Heat cd1.avi")))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void treatsRegularFilesAsSinglePart() {
        assertThat(StackedFiles.partCount(directory.resolve("Heat.mkv"))).isEqualTo(1);
        assertThat(StackedFiles.partCount(directory.resolve("README"))).isEqualTo(1);
    }

    @Test
    void assumesSeveralPartsWhenTheFolderCannotBeRead() {
        Path unreachable = directory.resolve("fehlt").resolve("Heat-cd1.avi");

        assertThat(StackedFiles.partCount(unreachable)).isEqualTo(StackedFiles.ASSUMED_PARTS);
    }
}
