package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tiktak.kmm.job.JobCancelledException;
import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.migration.ember.library.EmberEpisode;
import de.tiktak.kmm.migration.ember.library.EmberTvLibrary;
import de.tiktak.kmm.migration.ember.library.EmberTvShow;
import de.tiktak.kmm.migration.ember.library.EmberTvShowSource;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.source.ImportedSource;
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;
import de.tiktak.kmm.tvshow.EpisodeImport;
import de.tiktak.kmm.tvshow.TvImportResult;
import de.tiktak.kmm.tvshow.TvShowImport;
import de.tiktak.kmm.tvshow.TvShowImportService;
import de.tiktak.kmm.tvshow.TvShowImportService.ImportedTvShow;
import de.tiktak.kmm.tvshow.TvShowImportService.TvShowImportSession;

@ExtendWith(MockitoExtension.class)
class EmberTvImporterTest {

    private static final String EMBER_ROOT = "\\\\nas\\serien";
    private static final ImportedTvShow IMPORTED = new ImportedTvShow(7, new TvImportResult(true, false));

    @Mock
    private MediaSourceService sourceService;

    @Mock
    private TvShowImportService importService;

    @Mock
    private TvShowImportSession session;

    @Mock
    private JobContext context;

    @TempDir
    Path series;

    private EmberTvImporter importer;

    @BeforeEach
    void createImporter() {
        importer = new EmberTvImporter(new EmberSourceImporter(sourceService), importService, Clock.systemUTC());
    }

    @Test
    void importsShowsWithTheirEpisodesAndCountsMissingOnes() {
        givenSource();
        when(importService.startSession(anyCollection())).thenReturn(session);
        when(session.importShow(any())).thenReturn(IMPORTED);
        when(session.importEpisode(eq(IMPORTED), any())).thenReturn(new TvImportResult(true, false),
                new TvImportResult(false, true));

        EmberImportReport.TvShows report = run(library(show(1, 1), episode(1, 1), episode(1, 2)), mapped());

        assertThat(report).isEqualTo(new EmberImportReport.TvShows(1, 0, 0, 1, 1, 0, 3));
        var showImport = ArgumentCaptor.forClass(TvShowImport.class);
        verify(session).importShow(showImport.capture());
        assertThat(showImport.getValue().directoryPath()).isEqualTo(series.resolve("Lost").toString());
        var episodeImport = ArgumentCaptor.forClass(EpisodeImport.class);
        verify(session, times(2)).importEpisode(eq(IMPORTED), episodeImport.capture());
        assertThat(episodeImport.getAllValues()).extracting(EpisodeImport::filePath)
                .containsOnly(series.resolve("Lost").resolve("Lost S01.mkv").toString());
        verify(sourceService).importSource("Serien", series.toString(), MediaType.TV_SHOW, "de-DE");
        assertThat(showImport.getValue().language()).isEqualTo("fr-FR");
    }

    @Test
    void skipsShowsOfSourcesWithoutMappingAndTheirEpisodes() {
        when(importService.startSession(anyCollection())).thenReturn(session);

        EmberImportReport.TvShows report = run(library(show(1, 1), episode(1, 1), episode(2, 1)),
                new EmberImportRequest.PathMapping("D:\\Andere", series.toString()));

        assertThat(report.showsSkipped()).isEqualTo(1);
        assertThat(report.episodesSkipped()).as("also episodes of an unknown TV show").isEqualTo(2);
        verify(sourceService, never()).importSource(anyString(), anyString(), any(), any());
        verify(session, never()).importShow(any());
    }

    @Test
    void skipsShowsAndEpisodesWithInvalidPaths() {
        givenSource();
        when(importService.startSession(anyCollection())).thenReturn(session);
        when(session.importShow(any())).thenReturn(IMPORTED);
        when(session.importEpisode(eq(IMPORTED), any())).thenThrow(new InvalidPathException("x ", "Trailing char"));
        EmberTvShow broken = EmberTvShow.builder().id(2).sourceId(1).path(EMBER_ROOT + "\\Kaputt").title("Kaputt")
                .build();
        EmberEpisode unmapped = EmberEpisode.builder().id(9).showId(1).path("E:\\Fremd\\x.mkv").season(1).episode(9)
                .build();
        when(session.importShow(argThat(show -> show != null
                && show.metadata().title().equals("Kaputt")))).thenThrow(new InvalidPathException("Kaputt ", "x"));

        EmberImportReport.TvShows report = run(new EmberTvLibrary(List.of(source()), List.of(show(1, 1), broken),
                List.of(episode(1, 1), unmapped, episode(2, 1)), 0), mapped());

        assertThat(report.showsCreated()).isEqualTo(1);
        assertThat(report.showsSkipped()).isEqualTo(1);
        assertThat(report.episodesSkipped()).isEqualTo(3);
    }

    @Test
    void skipsEpisodesWhoseNumberContradictsTheFileName() {
        givenSource();
        when(importService.startSession(anyCollection())).thenReturn(session);
        when(session.importShow(any())).thenReturn(IMPORTED);
        when(session.importEpisode(eq(IMPORTED), any())).thenReturn(new TvImportResult(true, false));
        EmberEpisode misread = EmberEpisode.builder().id(1).showId(1)
                .path(EMBER_ROOT + "\\Lost\\tvr-lost-s01e02-720p.mkv").season(1).episode(720).build();
        EmberEpisode correct = EmberEpisode.builder().id(2).showId(1)
                .path(EMBER_ROOT + "\\Lost\\tvr-lost-s01e03-720p.mkv").season(1).episode(3).build();

        EmberImportProgress progress = new EmberImportProgress(false);
        importer.importLibrary(library(show(1, 1), misread, correct), EmberPathTranslator.of(List.of(mapped())),
                progress, context);
        EmberImportReport report = progress.toReport();

        assertThat(report.tvShows().episodesCreated()).isEqualTo(1);
        assertThat(report.tvShows().episodesSkipped()).isEqualTo(1);
        assertThat(report.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.key()).isEqualTo("migration.warning.episodeMismatch");
            assertThat(warning.args().get("path").toString()).contains("tvr-lost-s01e02-720p.mkv");
            assertThat(warning.args()).containsEntry("ember", "S01E720").containsEntry("file", "S01E02");
        });
    }

    @Test
    void stopsWhenCancelled() {
        givenSource();
        when(importService.startSession(anyCollection())).thenReturn(session);
        JobContext cancelled = mock(JobContext.class);
        doThrow(new JobCancelledException()).when(cancelled).throwIfCancellationRequested();

        assertThatExceptionOfType(JobCancelledException.class).isThrownBy(() -> importer.importLibrary(
                library(show(1, 1), episode(1, 1)), EmberPathTranslator.of(List.of(mapped())),
                new EmberImportProgress(false), cancelled));
        verify(session, never()).importShow(any());
    }

    private EmberImportReport.TvShows run(EmberTvLibrary library, EmberImportRequest.PathMapping mapping) {
        EmberImportProgress progress = new EmberImportProgress(false);
        importer.importLibrary(library, EmberPathTranslator.of(List.of(mapping)), progress, context);
        return progress.toReport().tvShows();
    }

    private EmberImportRequest.PathMapping mapped() {
        return new EmberImportRequest.PathMapping(EMBER_ROOT, series.toString());
    }

    private void givenSource() {
        MediaSource source = new MediaSource("Serien", series.toString(), MediaType.TV_SHOW);
        ReflectionTestUtils.setField(source, "id", 11L);
        when(sourceService.importSource(anyString(), anyString(), eq(MediaType.TV_SHOW), any()))
                .thenReturn(new ImportedSource(source, true));
    }

    private static EmberTvLibrary library(EmberTvShow show, EmberEpisode... episodes) {
        return new EmberTvLibrary(List.of(source()), List.of(show), List.of(episodes), 3);
    }

    private static EmberTvShowSource source() {
        return new EmberTvShowSource(1, null, EMBER_ROOT, "de-DE");
    }

    private static EmberTvShow show(long id, long sourceId) {
        return EmberTvShow.builder().id(id).sourceId(sourceId).path(EMBER_ROOT + "\\Lost").title("Lost")
                .language("fr-FR").build();
    }

    private static EmberEpisode episode(long showId, int number) {
        return EmberEpisode.builder().id(number).showId(showId).path(EMBER_ROOT + "\\Lost\\Lost S01.mkv").season(1)
                .episode(number).build();
    }
}
