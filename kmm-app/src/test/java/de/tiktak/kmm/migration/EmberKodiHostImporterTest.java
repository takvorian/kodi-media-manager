package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.kodi.KodiHostService;
import de.tiktak.kmm.kodi.KodiHostSettings;
import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHost;

@ExtendWith(MockitoExtension.class)
class EmberKodiHostImporterTest {

    private static final String MOVIES = Path.of("filme").toAbsolutePath().toString();
    private static final EmberPathTranslator TO_MEDIA = EmberPathTranslator.of(List.of(
            new EmberImportRequest.PathMapping("D:\\Filme", MOVIES)));

    @Mock
    private KodiHostService hostService;

    private final EmberImportProgress progress = new EmberImportProgress(false);

    @Test
    void importsHostsWithCredentialsAndMappedPaths() {
        when(hostService.importHost(any())).thenReturn(true, false);
        EmberKodiHost livingRoom = new EmberKodiHost("Wohnzimmer", "kodi.local", 8080, "kodi", "geheim", true, List.of(
                new EmberKodiHost.Source("Movie", "D:\\Filme", "smb://nas/filme/"),
                new EmberKodiHost.Source("Movie", "d:\\filme\\", "smb://nas/doppelt/"),
                new EmberKodiHost.Source("TV", "D:\\Serien", "smb://nas/serien/"),
                new EmberKodiHost.Source("Movie", null, "smb://nas/leer/")));
        EmberKodiHost unnamed = new EmberKodiHost(null, "192.168.1.20", 0, null, null, false, List.of());

        new EmberKodiHostImporter(hostService).importHosts(List.of(livingRoom, unnamed), TO_MEDIA, progress);

        ArgumentCaptor<KodiHostSettings> imported = ArgumentCaptor.forClass(KodiHostSettings.class);
        verify(hostService, times(2)).importHost(imported.capture());
        assertThat(imported.getAllValues()).containsExactly(
                new KodiHostSettings("Wohnzimmer", "kodi.local", 8080, "kodi", "geheim", true,
                        List.of(new KodiPathMapping(MOVIES, "smb://nas/filme/"))),
                new KodiHostSettings("192.168.1.20", "192.168.1.20", EmberKodiHostImporter.DEFAULT_PORT, null, null,
                        false, List.of()));
        EmberImportReport report = progress.toReport();
        assertThat(report.kodiHostsCreated()).isEqualTo(1);
        assertThat(report.kodiHostsUpdated()).isEqualTo(1);
        assertThat(report.warnings()).containsExactly(LocalizedText.of("migration.warning.kodiMappingMissing")
                .with("host", "Wohnzimmer").with("local", "D:\\Serien").with("remote", "smb://nas/serien/"));
    }

    @Test
    void skipsHostsWithoutAddressAndShortensLongNames() {
        when(hostService.importHost(any())).thenReturn(true);
        String longName = "K".repeat(KodiHostSettings.MAX_NAME_LENGTH + 10);

        new EmberKodiHostImporter(hostService).importHosts(List.of(
                new EmberKodiHost("Ohne Adresse", null, 8080, null, null, true, List.of()),
                new EmberKodiHost(longName, "kodi", 70_000, null, null, true, List.of())), TO_MEDIA, progress);

        ArgumentCaptor<KodiHostSettings> imported = ArgumentCaptor.forClass(KodiHostSettings.class);
        verify(hostService).importHost(imported.capture());
        assertThat(imported.getValue().name()).hasSize(KodiHostSettings.MAX_NAME_LENGTH);
        assertThat(imported.getValue().port()).isEqualTo(EmberKodiHostImporter.DEFAULT_PORT);
        assertThat(progress.toReport().warnings())
                .containsExactly(LocalizedText.of("migration.warning.kodiHostWithoutAddress"));
    }

    @Test
    void importsNothingWithoutHosts() {
        new EmberKodiHostImporter(hostService).importHosts(List.of(), TO_MEDIA, progress);

        verifyNoInteractions(hostService);
    }
}
