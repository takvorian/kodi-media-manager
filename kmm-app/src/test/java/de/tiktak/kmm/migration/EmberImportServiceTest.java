package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.job.JobTask;
import de.tiktak.kmm.migration.ember.EmberLibraryStatistics;
import de.tiktak.kmm.migration.ember.EmberProfile;
import de.tiktak.kmm.migration.ember.EmberProfileDetector;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import de.tiktak.kmm.migration.ember.EmberProfileInspection;
import de.tiktak.kmm.migration.ember.EmberProfileInspector;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHost;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHostsReader;
import de.tiktak.kmm.migration.ember.library.EmberLibrary;
import de.tiktak.kmm.migration.ember.library.EmberLibraryReader;
import de.tiktak.kmm.migration.ember.library.EmberTvLibrary;
import de.tiktak.kmm.migration.ember.library.EmberTvLibraryReader;
import de.tiktak.kmm.migration.ember.library.EmberTvShowSource;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsException;
import de.tiktak.kmm.migration.ember.settings.EmberAdvancedSettingsReader;
import de.tiktak.kmm.migration.ember.settings.EmberRenameSettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsReader;
import de.tiktak.kmm.i18n.LocalizedText;

@ExtendWith(MockitoExtension.class)
class EmberImportServiceTest {

    private static final Path PROFILE_DIRECTORY = Path.of("ember");
    private static final EmberProfile PROFILE = new EmberProfile(PROFILE_DIRECTORY,
            PROFILE_DIRECTORY.resolve("MyVideos48.emm"), 48, Optional.of(PROFILE_DIRECTORY.resolve("Settings.xml")));
    private static final EmberLibrary EMPTY_LIBRARY = new EmberLibrary(List.of(), List.of(), List.of(), List.of());

    @Mock
    private EmberProfileInspector profileInspector;

    @Mock
    private EmberProfileDetector profileDetector;

    @Mock
    private EmberLibraryReader libraryReader;

    @Mock
    private EmberTvLibraryReader tvLibraryReader;

    @Mock
    private EmberSettingsReader settingsReader;

    @Mock
    private EmberAdvancedSettingsReader advancedSettingsReader;

    @Mock
    private EmberKodiHostsReader kodiHostsReader;

    @Mock
    private EmberLibraryImporter importer;

    @Mock
    private EmberKodiHostImporter kodiHostImporter;

    @Mock
    private JobService jobService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private EmberProfileUpload profileUpload;

    private final SimpleTransactionStatus status = new SimpleTransactionStatus();

    @Test
    void startsJobWithTitleOfTheRun() {
        when(profileUpload.directory()).thenReturn(PROFILE_DIRECTORY);
        when(profileDetector.detect(PROFILE_DIRECTORY)).thenReturn(PROFILE);

        service().start(new EmberImportRequest(List.of(), false, false, false, true));

        verify(jobService).submit(eq(EmberImportService.JOB_KEY), eq("Ember-Import (Probelauf)"), any());
    }

    @Test
    void rollsBackDryRunButReportsTheResult() {
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);

        EmberImportReport report = service().run(PROFILE,
                new EmberImportRequest(List.of(), false, false, false, true), mock(JobContext.class));

        assertThat(report.dryRun()).isTrue();
        assertThat(status.isRollbackOnly()).isTrue();
        verify(settingsReader, never()).read(any());
    }

    @Test
    void continuesWithoutSettingsThatCannotBeRead() {
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);
        EmberSettingsException unreadable = mock(EmberSettingsException.class);
        when(unreadable.getMessage()).thenReturn("kaputt");
        when(settingsReader.read(any())).thenThrow(unreadable);

        EmberImportReport report = service().run(PROFILE,
                new EmberImportRequest(List.of(), true, false, false, false), mock(JobContext.class));

        assertThat(report.warnings()).containsExactly("Die Ember-Einstellungen konnten nicht gelesen werden: kaputt");
        assertThat(status.isRollbackOnly()).isFalse();
        verify(importer).importLibrary(eq(EMPTY_LIBRARY), eq(Optional.empty()), eq(Optional.empty()), any(), any(),
                any());
        verify(tvLibraryReader, never()).read(any());
    }

    @Test
    void addsTheRenameSettingsAndContinuesWithoutThemIfUnreadable() {
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);
        EmberSettings settings = new EmberSettings(1, null, List.of(), List.of(), Optional.empty(), null, null);
        when(settingsReader.read(any())).thenReturn(settings);
        EmberRenameSettings rename = new EmberRenameSettings("$T", null, null, null, null, null, null, null, null,
                null);
        when(advancedSettingsReader.readRenameSettings(PROFILE)).thenReturn(Optional.of(rename));
        EmberImportRequest request = new EmberImportRequest(List.of(), true, false, false, false);

        service().run(PROFILE, request, mock(JobContext.class));

        verify(importer).importLibrary(eq(EMPTY_LIBRARY), eq(Optional.empty()),
                eq(Optional.of(settings.withRename(rename))), any(), any(), any());

        EmberSettingsException unreadable = mock(EmberSettingsException.class);
        when(unreadable.getMessage()).thenReturn("kaputt");
        when(advancedSettingsReader.readRenameSettings(PROFILE)).thenThrow(unreadable);

        EmberImportReport report = service().run(PROFILE, request, mock(JobContext.class));

        assertThat(report.warnings())
                .containsExactly("Die Einstellungen zum Umbenennen konnten nicht gelesen werden: kaputt");
        verify(importer).importLibrary(eq(EMPTY_LIBRARY), eq(Optional.empty()), eq(Optional.of(settings)), any(),
                any(), any());
    }

    @Test
    void readsTheTvLibraryOnlyOnRequest() {
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);
        EmberTvLibrary tvLibrary = new EmberTvLibrary(List.of(), List.of(), List.of(), 0);
        when(tvLibraryReader.read(PROFILE.databaseFile())).thenReturn(tvLibrary);

        EmberImportReport report = service().run(PROFILE,
                new EmberImportRequest(List.of(), false, false, true, false), mock(JobContext.class));

        assertThat(report.tvShows().episodesCreated()).isZero();
        verify(importer).importLibrary(eq(EMPTY_LIBRARY), eq(Optional.of(tvLibrary)), eq(Optional.empty()), any(),
                any(), any());
    }

    @Test
    void inspectsMovieAndTvSources() {
        when(profileInspector.inspect(PROFILE_DIRECTORY)).thenReturn(new EmberProfileInspection(PROFILE,
                new EmberLibraryStatistics(0, 0, 0, 0, 0, 0), Optional.empty()));
        when(tvLibraryReader.readSources(PROFILE.databaseFile()))
                .thenReturn(List.of(new EmberTvShowSource(2, "serien", "a:\\medien\\serien", "de-DE")));

        assertThat(service().inspect(PROFILE_DIRECTORY).tvSources())
                .containsExactly(new EmberInspectionResponse.Source("serien", "a:\\medien\\serien"));
    }

    @Test
    void importsKodiHostsOnlyOnRequest() {
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);
        List<EmberKodiHost> hosts = List.of(new EmberKodiHost("Wohnzimmer", "kodi", 8080, null, null, true, List.of()));
        when(kodiHostsReader.read(PROFILE)).thenReturn(hosts);

        service().run(PROFILE, new EmberImportRequest(List.of(), false, false, false, false),
                mock(JobContext.class));
        service().run(PROFILE, new EmberImportRequest(List.of(), false, true, false, false),
                mock(JobContext.class));

        verify(kodiHostImporter).importHosts(eq(List.of()), any(), any());
        verify(kodiHostImporter).importHosts(eq(hosts), any(), any());
    }

    @Test
    void continuesWithoutKodiHostsThatCannotBeRead() {
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);
        when(kodiHostsReader.read(PROFILE)).thenThrow(EmberProfileException.unreadableKodiSettings(
                PROFILE_DIRECTORY.resolve("Interface.Kodi.xml"), new IllegalStateException()));

        EmberImportReport report = service().run(PROFILE,
                new EmberImportRequest(List.of(), false, true, false, false), mock(JobContext.class));

        assertThat(report.warnings()).singleElement().asString()
                .startsWith("Die Kodi-Geräte konnten nicht gelesen werden: ");
        verify(kodiHostImporter).importHosts(eq(List.of()), any(), any());
    }

    @Test
    void inspectsAProfileEvenWithUnreadableKodiSettings() {
        when(profileInspector.inspect(PROFILE_DIRECTORY)).thenReturn(new EmberProfileInspection(PROFILE,
                new EmberLibraryStatistics(0, 0, 0, 0, 0, 0), Optional.empty()));
        when(kodiHostsReader.read(PROFILE)).thenThrow(EmberProfileException.unreadableKodiSettings(
                PROFILE_DIRECTORY.resolve("Interface.Kodi.xml"), new IllegalStateException()));

        assertThat(service().inspect(PROFILE_DIRECTORY).kodiHosts()).isEmpty();
    }

    @Test
    void runsTheImportInsideTheJob() {
        when(profileUpload.directory()).thenReturn(PROFILE_DIRECTORY);
        when(profileDetector.detect(PROFILE_DIRECTORY)).thenReturn(PROFILE);
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);
        EmberImportService service = service();

        service.start(new EmberImportRequest(List.of(), false, false, false, false));

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(anyString(), anyString(), task.capture());
        assertThat(task.getValue().run(mock(JobContext.class))).isInstanceOf(EmberImportReport.class);
        verify(profileUpload).discard();
    }

    @Test
    void keepsTheUploadAfterADryRun() {
        when(profileUpload.directory()).thenReturn(PROFILE_DIRECTORY);
        when(profileDetector.detect(PROFILE_DIRECTORY)).thenReturn(PROFILE);
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);

        service().start(new EmberImportRequest(List.of(), false, false, false, true));

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(anyString(), anyString(), task.capture());
        task.getValue().run(mock(JobContext.class));
        verify(profileUpload, never()).discard();
    }

    @Test
    void deletesTheUploadWhenTheImportFails() {
        when(profileUpload.directory()).thenReturn(PROFILE_DIRECTORY);
        when(profileDetector.detect(PROFILE_DIRECTORY)).thenReturn(PROFILE);
        when(libraryReader.read(PROFILE.databaseFile())).thenThrow(new IllegalStateException("kaputt"));

        service().start(new EmberImportRequest(List.of(), false, false, false, false));

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(anyString(), anyString(), task.capture());
        assertThatThrownBy(() -> task.getValue().run(mock(JobContext.class))).hasMessage("kaputt");
        verify(profileUpload).discard();
    }

    @Test
    void analyzesAnUploadAndDiscardsItIfItIsUnusable() {
        List<MultipartFile> files = List.of(mock(MultipartFile.class));
        when(profileUpload.directory()).thenReturn(PROFILE_DIRECTORY);
        when(profileInspector.inspect(PROFILE_DIRECTORY)).thenReturn(new EmberProfileInspection(PROFILE,
                new EmberLibraryStatistics(5, 0, 0, 0, 0, 0), Optional.empty()));

        assertThat(service().upload(files).library().movies()).isEqualTo(5);
        verify(profileUpload).store(files);
        verify(profileUpload, never()).discard();

        when(profileInspector.inspect(PROFILE_DIRECTORY)).thenThrow(new IllegalStateException("kaputt"));

        assertThatThrownBy(() -> service().upload(files)).hasMessage("kaputt");
        verify(profileUpload).discard();
    }

    @Test
    void leavesTheFilesOfARunningImportAlone() {
        when(jobService.findAll()).thenReturn(List.of(new JobSnapshot(UUID.randomUUID(), EmberImportService.JOB_KEY,
                LocalizedText.literal("Ember-Import"), JobStatus.RUNNING, 0, 0, null, null, Instant.now(), null)));

        assertThatThrownBy(() -> service().upload(List.of())).isInstanceOf(EmberProfileUploadException.class);
        assertThatThrownBy(() -> service().discardUpload()).isInstanceOf(EmberProfileUploadException.class);

        verify(profileUpload, never()).store(any());
        verify(profileUpload, never()).discard();
    }

    @Test
    void discardsTheUploadOnRequest() {
        service().discardUpload();

        verify(profileUpload).discard();
    }

    @Test
    void rejectsInvalidProfileBeforeStarting() {
        when(profileUpload.directory()).thenReturn(PROFILE_DIRECTORY);
        when(profileDetector.detect(any())).thenThrow(new IllegalStateException("kein Profil"));

        assertThatThrownBy(() -> service().start(new EmberImportRequest(List.of(), false, false, false, false)))
                .hasMessage("kein Profil");
        verify(jobService, never()).submit(anyString(), anyString(), any());
    }

    private void givenTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(status);
    }

    private EmberImportService service() {
        return new EmberImportService(profileInspector, profileDetector, libraryReader, tvLibraryReader, settingsReader,
                advancedSettingsReader, kodiHostsReader, importer, kodiHostImporter, jobService, new TransactionTemplate(transactionManager),
                profileUpload);
    }
}
