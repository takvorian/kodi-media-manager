package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.job.JobCancelledException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.migration.ember.library.EmberLibrary;
import de.tiktak.kmm.migration.ember.library.EmberMovie;
import de.tiktak.kmm.migration.ember.library.EmberMovie.EmberSetLink;
import de.tiktak.kmm.migration.ember.library.EmberMovieSet;
import de.tiktak.kmm.migration.ember.library.EmberMovieSource;
import de.tiktak.kmm.movie.MovieImport;
import de.tiktak.kmm.movie.MovieImportResult;
import de.tiktak.kmm.movie.MovieImportService;
import de.tiktak.kmm.movie.MovieImportService.MovieImportSession;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;
import de.tiktak.kmm.source.ImportedSource;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceConflictException;
import de.tiktak.kmm.source.MediaSourceService;

@ExtendWith(MockitoExtension.class)
class EmberLibraryImporterTest {

    @Mock
    private MediaSourceService sourceService;

    @Mock
    private MovieImportService movieImportService;

    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private EmberLibraryImporter importer;

    @TempDir
    Path library;

    private final MovieImportSession session = mock(MovieImportSession.class);
    private final RecordingContext context = new RecordingContext(false);

    @Test
    void keepsPathsUnchangedWithoutMappingRules() {
        String moviePath = library.resolve("Heat.mkv").toString();
        givenSource(library.toString(), 11L, true);
        when(movieImportService.startSession(anyCollection())).thenReturn(session);
        when(session.importMovie(any())).thenReturn(new MovieImportResult(true, true));

        EmberImportReport report = run(library(source(library.toString()), movie(1, moviePath)), request(List.of()));

        assertThat(report.moviesCreated()).isEqualTo(1);
        assertThat(report.sourcesCreated()).isEqualTo(1);
        assertThat(report.moviesWithoutNfo()).isZero();
        assertThat(context.progress).containsExactly("0/1 Film 1", "1/1 null");
    }

    @Test
    void skipsSourcesWithoutValidMappingAndTheirMovies() {
        when(movieImportService.startSession(anyCollection())).thenReturn(session);

        EmberImportReport report = run(library(source("D:\\Filme"), movie(1, "D:\\Filme\\Heat.mkv")),
                request(List.of(new EmberImportRequest.PathMapping("E:\\Andere", library.toString()))));

        assertThat(report.skippedSources()).singleElement().asString().contains("keine gültige Pfad-Zuordnung");
        assertThat(report.moviesSkipped()).isEqualTo(1);
        verify(sourceService, never()).importSource(anyString(), anyString(), any());
    }

    @Test
    void reportsConflictingSources() {
        MediaSourceConflictException conflict = conflict("überschneidet sich");
        when(sourceService.importSource(anyString(), anyString(), eq(MediaType.MOVIE))).thenThrow(conflict);
        when(movieImportService.startSession(anyCollection())).thenReturn(session);

        EmberImportReport report = run(library(source("D:\\Filme"), movie(1, "D:\\Filme\\Heat.mkv")), mapped());

        assertThat(report.skippedSources()).singleElement().asString().contains("überschneidet sich");
        assertThat(report.moviesSkipped()).isEqualTo(1);
    }

    @Test
    void reportsUnreachableDirectoriesMissingNfosAndSeveralSets() {
        givenSource(library.resolve("Filme").toString(), 11L, false);
        when(movieImportService.startSession(anyCollection())).thenReturn(session);
        when(session.importMovie(any())).thenReturn(new MovieImportResult(false, false));
        EmberMovie inTwoSets = EmberMovie.builder().id(2).sourceId(1).path("D:\\Filme\\Matrix.mkv").title("Matrix")
                .sets(List.of(new EmberSetLink(1, 0), new EmberSetLink(2, 0))).build();

        EmberImportReport report = run(library(source("D:\\Filme"), inTwoSets), request(List.of(
                new EmberImportRequest.PathMapping("D:\\Filme", library.resolve("fehlt").toString()))));

        assertThat(report.sourcesReused()).isEqualTo(1);
        assertThat(report.moviesUpdated()).isEqualTo(1);
        assertThat(report.missingFiles()).containsExactly("Matrix");
        assertThat(report.warnings()).anySatisfy(warning -> assertThat(warning).contains("nicht erreichbar"))
                .anySatisfy(warning -> assertThat(warning).contains("mehreren Sets"));
    }

    @Test
    void skipsMoviesWhosePathIsInvalidOnThisSystem() {
        givenSource(library.toString(), 11L, true);
        when(movieImportService.startSession(anyCollection())).thenReturn(session);
        when(session.importMovie(any())).thenThrow(new InvalidPathException("Film .mkv", "Trailing char"));

        EmberImportReport report = run(library(source("D:\\Filme"), movie(1, "D:\\Filme\\Film .mkv")), mapped());

        assertThat(report.moviesSkipped()).isEqualTo(1);
        assertThat(report.warnings()).singleElement().asString().contains("übersprungen");
    }

    @Test
    void savesSettingsOnlyOutsideDryRun() {
        when(movieImportService.startSession(anyCollection())).thenReturn(session);
        when(settingsService.current()).thenReturn(KmmSettings.DEFAULTS);
        EmberLibrary withExcluded = new EmberLibrary(List.of(), List.of(), List.of(), List.of("D:\\Filme\\Privat"));

        EmberImportReport dryRun = run(withExcluded, new EmberImportRequest("ember", mappings(), true, true));
        verify(settingsService, never()).update(any());
        EmberImportReport real = run(withExcluded, new EmberImportRequest("ember", mappings(), true, false));

        assertThat(dryRun.settingsChanges()).isEqualTo(real.settingsChanges()).hasSize(1);
        verify(settingsService).update(any());
    }

    @Test
    void stopsWhenCancelled() {
        givenSource(library.toString(), 11L, true);
        when(movieImportService.startSession(anyCollection())).thenReturn(session);

        assertThatExceptionOfType(JobCancelledException.class).isThrownBy(() -> importer.importLibrary(
                library(source("D:\\Filme"), movie(1, "D:\\Filme\\Heat.mkv")), Optional.empty(), mapped(),
                new EmberImportProgress(false), new RecordingContext(true)));
        verify(session, never()).importMovie(any(MovieImport.class));
    }

    private EmberImportReport run(EmberLibrary emberLibrary, EmberImportRequest request) {
        EmberImportProgress progress = new EmberImportProgress(request.dryRun());
        importer.importLibrary(emberLibrary, Optional.empty(), request, progress, context);
        return progress.toReport();
    }

    private void givenSource(String path, long id, boolean created) {
        MediaSource source = new MediaSource("Filme", path, MediaType.MOVIE);
        ReflectionTestUtils.setField(source, "id", id);
        when(sourceService.importSource(anyString(), anyString(), eq(MediaType.MOVIE)))
                .thenReturn(new ImportedSource(source, created));
    }

    private EmberImportRequest mapped() {
        return request(mappings());
    }

    private List<EmberImportRequest.PathMapping> mappings() {
        return List.of(new EmberImportRequest.PathMapping("D:\\Filme", library.toString()));
    }

    private static EmberImportRequest request(List<EmberImportRequest.PathMapping> mappings) {
        return new EmberImportRequest("ember", mappings, false, false);
    }

    private static EmberLibrary library(EmberMovieSource source, EmberMovie movie) {
        return new EmberLibrary(List.of(source), List.of(movie), List.of(new EmberMovieSet(1, "Set", null, null)),
                List.of());
    }

    private static EmberMovieSource source(String path) {
        return new EmberMovieSource(1, "Filme", path, "de-DE");
    }

    private static EmberMovie movie(long id, String path) {
        return EmberMovie.builder().id(id).sourceId(1).path(path).title("Film " + id).build();
    }

    private static MediaSourceConflictException conflict(String detail) {
        MediaSourceConflictException exception = mock(MediaSourceConflictException.class);
        when(exception.getBody()).thenReturn(org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.CONFLICT, detail));
        return exception;
    }

    /** Merkt sich die Fortschrittsmeldungen als „erledigt/gesamt Nachricht“. */
    private static final class RecordingContext implements JobContext {

        private final boolean cancelled;
        private final List<String> progress = new ArrayList<>();

        private RecordingContext(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public void reportProgress(int done, int total, String message) {
            progress.add(done + "/" + total + " " + message);
        }

        @Override
        public boolean isCancellationRequested() {
            return cancelled;
        }
    }
}
