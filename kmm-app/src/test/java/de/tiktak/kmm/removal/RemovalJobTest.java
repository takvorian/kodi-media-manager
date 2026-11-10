package de.tiktak.kmm.removal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;

class RemovalJobTest {

    @TempDir
    Path source;

    private final JobContext context = mock(JobContext.class);

    @Test
    void deletesTheFilesThenRemovesTheEntries() throws IOException {
        Path video = Files.writeString(Files.createDirectories(source.resolve("Heat")).resolve("Heat.mkv"), "x");
        RemovalFiles files = files(video);
        AtomicBoolean removed = new AtomicBoolean();

        RemovalResult result = RemovalJob.run(files, true, 1, () -> removed.set(true), context);

        assertThat(result).isEqualTo(new RemovalResult(1, 1, List.of()));
        assertThat(video).doesNotExist();
        assertThat(removed).isTrue();
        verify(context).reportProgress(0, 2, LocalizedText.of("removal.progress.files"));
        verify(context).reportProgress(1, 2, LocalizedText.of("removal.progress.library"));
    }

    @Test
    void leavesTheFilesAloneForTheLibraryOnly() throws IOException {
        Path video = Files.writeString(source.resolve("Heat.mkv"), "x");
        AtomicBoolean removed = new AtomicBoolean();

        RemovalResult result = RemovalJob.run(files(video), false, 2, () -> removed.set(true), context);

        assertThat(result).isEqualTo(new RemovalResult(2, 0, List.of()));
        assertThat(video).exists();
        assertThat(removed).isTrue();
    }

    @Test
    void rejectsDeletingBlockedFilesButNotTheLibraryOnly() {
        RemovalFiles files = new RemovalFiles();
        files.block(LocalizedText.of("removal.note.sharedDiscFolder"));

        assertThatThrownBy(() -> RemovalJob.requireAllowed(files, true)).isInstanceOf(RemovalProblemException.class);
        assertThatCode(() -> RemovalJob.requireAllowed(files, false)).doesNotThrowAnyException();
    }

    @Test
    void saysInTheTitleWhetherFilesAreDeleted() {
        LocalizedText title = LocalizedText.of("removal.job.entry").with("title", "Heat");

        assertThat(RemovalJob.title(title, true).args()).containsEntry(RemovalJob.MODE_ARGUMENT, "files");
        assertThat(RemovalJob.title(title, false).args()).containsEntry(RemovalJob.MODE_ARGUMENT, "library");
    }

    private RemovalFiles files(Path video) {
        RemovalFiles files = new RemovalFiles();
        files.scope(source, "Filme", source).addWithCompanions(video, List.of());
        return files;
    }
}
