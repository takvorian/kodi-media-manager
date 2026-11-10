package de.tiktak.kmm.movie;

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
import de.tiktak.kmm.movie.MovieRemoval.RemovedMovie;
import de.tiktak.kmm.removal.RemovalFiles;
import de.tiktak.kmm.removal.RemovalProblemException;
import de.tiktak.kmm.removal.RemovalResult;

@ExtendWith(MockitoExtension.class)
class MovieRemovalServiceTest {

    @TempDir
    Path source;

    @Mock
    private MovieRemovalPlanner planner;

    @Mock
    private MovieRemover remover;

    @Mock
    private JobService jobService;

    @InjectMocks
    private MovieRemovalService service;

    private final JobContext context = mock(JobContext.class);

    @Test
    void previewsWithoutChangingAnything() throws IOException {
        Path video = file(source.resolve("Heat/Heat.mkv"));
        when(planner.movies(List.of(1L))).thenReturn(removal(video));

        assertThat(service.plan(List.of(1L)).files()).containsExactly(video.toString());
        assertThat(video).exists();
    }

    @Test
    void deletesASingleMovieWithItsFolder() throws IOException {
        Path video = file(source.resolve("Heat/Heat.mkv"));
        Path other = file(source.resolve("Ronin/Ronin.mkv"));
        MovieRemoval removal = removal(video);
        when(planner.movies(List.of(1L))).thenReturn(removal);

        service.remove(List.of(1L), true);
        RemovalResult result = run("removal:movie:1", LocalizedText.of("removal.job.entry").with("title", "Heat")
                .with("mode", "files"));

        assertThat(result).isEqualTo(new RemovalResult(1, 1, List.of()));
        assertThat(source.resolve("Heat")).doesNotExist();
        assertThat(other).exists();
        verify(remover).remove(removal);
    }

    @Test
    void removesASelectionFromTheLibraryOnly() throws IOException {
        Path heat = file(source.resolve("Heat.mkv"));
        Path ronin = file(source.resolve("Ronin.mkv"));
        RemovalFiles files = new RemovalFiles();
        files.scope(source, "Filme", source).addWithCompanions(heat, List.of("Ronin"));
        files.scope(source, "Filme", source).addWithCompanions(ronin, List.of("Heat"));
        MovieRemoval removal = new MovieRemoval(List.of(new RemovedMovie(1, "Heat", heat.toString()),
                new RemovedMovie(2, "Ronin", ronin.toString())), files, List.of());
        when(planner.movies(List.of(1L, 2L))).thenReturn(removal);

        service.remove(List.of(1L, 2L), false);
        RemovalResult result = run("removal:movies", LocalizedText.of("removal.job.movies").with("count", 2)
                .with("mode", "library"));

        assertThat(result).isEqualTo(new RemovalResult(2, 0, List.of()));
        assertThat(heat).exists();
        assertThat(ronin).exists();
        verify(remover).remove(removal);
    }

    @Test
    void refusesToDeleteBlockedFiles() throws IOException {
        MovieRemoval removal = removal(file(source.resolve("Heat/Heat.mkv")));
        removal.files().block(LocalizedText.of("removal.note.sharedDiscFolder"));
        when(planner.movies(List.of(1L))).thenReturn(removal);

        assertThatThrownBy(() -> service.remove(List.of(1L), true)).isInstanceOf(RemovalProblemException.class);
        verify(jobService, never()).submit(anyString(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private RemovalResult run(String key, LocalizedText title) {
        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(eq(key), eq(title), task.capture());
        return (RemovalResult) task.getValue().run(context);
    }

    private MovieRemoval removal(Path video) {
        RemovalFiles files = new RemovalFiles();
        files.scope(source, "Filme", source).addWithCompanions(video, List.of());
        return new MovieRemoval(List.of(new RemovedMovie(1, "Heat", video.toString())), files,
                List.of(LocalizedText.literal("Heat")));
    }

    private static Path file(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, "x");
    }
}
