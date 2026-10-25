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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import de.tiktak.kmm.job.JobContext;
import de.tiktak.kmm.job.JobService;
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
import de.tiktak.kmm.migration.ember.settings.EmberSettingsException;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsReader;

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
    private EmberSettingsReader settingsReader;

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

    private final SimpleTransactionStatus status = new SimpleTransactionStatus();

    @Test
    void startsJobWithTitleOfTheRun() {
        when(profileDetector.detect(PROFILE_DIRECTORY)).thenReturn(PROFILE);

        service().start(new EmberImportRequest("ember", List.of(), false, false, true));

        verify(jobService).submit(eq(EmberImportService.JOB_KEY), eq("Ember-Import (Probelauf)"), any());
    }

    @Test
    void rollsBackDryRunButReportsTheResult() {
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);

        EmberImportReport report = service().run(PROFILE, new EmberImportRequest("ember", List.of(), false, false, true),
                mock(JobContext.class));

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

        EmberImportReport report = service().run(PROFILE, new EmberImportRequest("ember", List.of(), true, false, false),
                mock(JobContext.class));

        assertThat(report.warnings()).containsExactly("Die Ember-Einstellungen konnten nicht gelesen werden: kaputt");
        assertThat(status.isRollbackOnly()).isFalse();
        verify(importer).importLibrary(eq(EMPTY_LIBRARY), eq(Optional.empty()), any(), any(), any());
    }

    @Test
    void importsKodiHostsOnlyOnRequest() {
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);
        List<EmberKodiHost> hosts = List.of(new EmberKodiHost("Wohnzimmer", "kodi", 8080, null, null, true, List.of()));
        when(kodiHostsReader.read(PROFILE)).thenReturn(hosts);

        service().run(PROFILE, new EmberImportRequest("ember", List.of(), false, false, false), mock(JobContext.class));
        service().run(PROFILE, new EmberImportRequest("ember", List.of(), false, true, false), mock(JobContext.class));

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
                new EmberImportRequest("ember", List.of(), false, true, false), mock(JobContext.class));

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
        when(profileDetector.detect(PROFILE_DIRECTORY)).thenReturn(PROFILE);
        givenTransaction();
        when(libraryReader.read(PROFILE.databaseFile())).thenReturn(EMPTY_LIBRARY);
        EmberImportService service = service();

        service.start(new EmberImportRequest("ember", List.of(), false, false, false));

        ArgumentCaptor<JobTask<?>> task = ArgumentCaptor.forClass(JobTask.class);
        verify(jobService).submit(anyString(), anyString(), task.capture());
        assertThat(task.getValue().run(mock(JobContext.class))).isInstanceOf(EmberImportReport.class);
    }

    @Test
    void rejectsInvalidProfileBeforeStarting() {
        when(profileDetector.detect(any())).thenThrow(new IllegalStateException("kein Profil"));

        assertThatThrownBy(() -> service().start(new EmberImportRequest("leer", List.of(), false, false, false)))
                .hasMessage("kein Profil");
        verify(jobService, never()).submit(anyString(), anyString(), any());
    }

    private void givenTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(status);
    }

    private EmberImportService service() {
        return new EmberImportService(profileInspector, profileDetector, libraryReader, settingsReader,
                kodiHostsReader, importer, kodiHostImporter, jobService, new TransactionTemplate(transactionManager));
    }
}
