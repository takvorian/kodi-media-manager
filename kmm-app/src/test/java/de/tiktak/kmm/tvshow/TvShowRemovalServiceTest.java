package de.tiktak.kmm.tvshow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobTask;
import de.tiktak.kmm.removal.RemovalFiles;
import de.tiktak.kmm.removal.RemovalPlan;
import de.tiktak.kmm.removal.RemovalProblemException;
import de.tiktak.kmm.removal.RemovalRequest;
import de.tiktak.kmm.removal.RemovalResult;
import de.tiktak.kmm.tvshow.EpisodesRemovedEvent.RemovedEpisode;

@ExtendWith(MockitoExtension.class)
class TvShowRemovalServiceTest {

    private static final RemovalRequest LIBRARY_ONLY = new RemovalRequest(false);
    private static final RemovalRequest WITH_FILES = new RemovalRequest(true);

    @TempDir
    Path source;

    @Mock
    private TvShowRemovalPlanner planner;

    @Mock
    private TvShowRemover remover;

    @Mock
    private JobService jobService;

    @InjectMocks
    private TvShowRemovalService service;

    private final JobContext context = mock(JobContext.class);

    @Test
    void previewsWithoutChangingAnything() throws IOException {
        Path video = file(source.resolve("ALF/S01E01.avi"));
        TvShowRemoval removal = removal(video, false);
        when(planner.episode(1)).thenReturn(removal);
        when(planner.season(7, 1)).thenReturn(removal);
        when(planner.show(7)).thenReturn(removal);

        RemovalPlan plan = service.planEpisode(1);

        assertThat(plan.files()).containsExactly(video.toString());
        assertThat(service.planSeason(7, 1)).isEqualTo(plan);
        assertThat(service.planShow(7)).isEqualTo(plan);
        assertThat(video).exists();
        verify(remover, never()).remove(any());
    }

    @Test
    void removesOnlyFromTheLibraryAndKeepsTheFiles() throws IOException {
        Path video = file(source.resolve("ALF/S01E01.avi"));
        TvShowRemoval removal = removal(video, false);
        when(planner.episode(1)).thenReturn(removal);

        RemovalResult result = runJob(() -> service.removeEpisode(1, LIBRARY_ONLY), "removal:episode:1");

        assertThat(result).isEqualTo(new RemovalResult(1, 0, List.of()));
        assertThat(video).exists();
        verify(remover).remove(removal);
    }

    @Test
    void deletesTheFilesAndReportsFilesThatCannotBeDeleted() throws IOException {
        Path video = file(source.resolve("ALF/Staffel 1/S01E01.avi"));
        Path nfo = file(source.resolve("ALF/Staffel 1/S01E01.nfo"));
        TvShowRemoval removal = removal(video, false);
        when(planner.season(7, 1)).thenReturn(removal);
        removal.plan();
        Files.delete(nfo);
        file(nfo.resolve("blockiert.txt"));

        RemovalResult result = runJob(() -> service.removeSeason(7, 1, WITH_FILES), "removal:season:7:1");

        assertThat(result.removedEntries()).isEqualTo(1);
        assertThat(result.deletedFiles()).isEqualTo(1);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.key()).isEqualTo("removal.deleteFailed");
            assertThat(failure.args()).containsEntry("file", nfo.toString());
        });
        assertThat(video).doesNotExist();
        assertThat(source).exists();
        verify(remover).remove(removal);
    }

    @Test
    void removesAWholeShowWithItsFolder() throws IOException {
        Path video = file(source.resolve("ALF/Staffel 1/S01E01.avi"));
        TvShowRemoval removal = showRemoval(source.resolve("ALF"));
        when(planner.show(7)).thenReturn(removal);

        RemovalResult result = runJob(() -> service.removeShow(7, WITH_FILES), "removal:tvshow:7");

        assertThat(result.deletedFiles()).isEqualTo(1);
        assertThat(video).doesNotExist();
        assertThat(source.resolve("ALF")).doesNotExist();
        assertThat(source).exists();
    }

    @Test
    void refusesToDeleteFilesOutsideTheSourceButRemovesFromTheLibrary(@TempDir Path elsewhere) throws IOException {
        Path video = file(elsewhere.resolve("S01E01.avi"));
        when(planner.episode(1)).thenReturn(removal(video, false));

        assertThatThrownBy(() -> service.removeEpisode(1, WITH_FILES)).isInstanceOf(RemovalProblemException.class);
        verify(jobService, never()).submit(anyString(), any(), any());

        runJob(() -> service.removeEpisode(1, LIBRARY_ONLY), "removal:episode:1");
        assertThat(video).exists();
    }

    @Test
    void namesTheJobsAfterWhatIsRemoved() throws IOException {
        Path video = file(source.resolve("ALF/S01E01.avi"));
        when(planner.episode(1)).thenReturn(removal(video, false));
        when(planner.season(7, 2)).thenReturn(removal(video, false));

        service.removeEpisode(1, WITH_FILES);
        service.removeSeason(7, 2, LIBRARY_ONLY);

        ArgumentCaptor<LocalizedText> titles = ArgumentCaptor.forClass(LocalizedText.class);
        verify(jobService, org.mockito.Mockito.times(2)).submit(anyString(), titles.capture(), any());
        assertThat(titles.getAllValues().get(0)).isEqualTo(LocalizedText.of("removal.job.entry")
                .with("title", "ALF S01E01").with("mode", "files"));
        assertThat(titles.getAllValues().get(1)).isEqualTo(LocalizedText.of("removal.job.season")
                .with("title", "ALF").with("season", 2).with("mode", "library"));
    }

    @SuppressWarnings("unchecked")
    private RemovalResult runJob(Runnable start, String key) {
        start.run();
        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService, org.mockito.Mockito.atLeastOnce()).submit(eq(key), any(), task.capture());
        return (RemovalResult) task.getValue().run(context);
    }

    private TvShowRemoval removal(Path video, boolean wholeShow) {
        RemovalFiles files = new RemovalFiles();
        Path showDirectory = source.resolve("ALF");
        files.scope(source, "Serien", showDirectory).addWithCompanions(video, List.of());
        return new TvShowRemoval(7, "ALF", showDirectory.toString(), "ALF S01E01", List.of(1L),
                List.of(new RemovedEpisode(video.toString(), 1, 1)), Set.of(1), wholeShow, files,
                List.of(LocalizedText.literal("ALF S01E01")));
    }

    private TvShowRemoval showRemoval(Path showDirectory) {
        RemovalFiles files = new RemovalFiles();
        files.scope(source, "Serien", source).addDirectory(showDirectory);
        return new TvShowRemoval(7, "ALF", showDirectory.toString(), "ALF", List.of(1L), List.of(), Set.of(1), true,
                files, List.of(LocalizedText.literal("ALF")));
    }

    private static Path file(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }
}
