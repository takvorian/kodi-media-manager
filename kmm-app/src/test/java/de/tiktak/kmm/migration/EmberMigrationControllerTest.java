package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.migration.ember.EmberLibraryStatistics;
import de.tiktak.kmm.migration.ember.EmberProfile;
import de.tiktak.kmm.migration.ember.EmberProfileException;
import de.tiktak.kmm.migration.ember.EmberProfileInspection;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHost;
import de.tiktak.kmm.migration.ember.library.EmberMovieSource;
import de.tiktak.kmm.migration.ember.library.EmberTvShowSource;
import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = EmberMigrationController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class EmberMigrationControllerTest {

    private static final String UPLOAD_PATH = EmberMigrationController.BASE_PATH + "/upload";
    private static final MockMultipartFile DATABASE = new MockMultipartFile("files", "MyVideos48.emm", null, new byte[1]);
    private static final String IMPORT_PATH = EmberMigrationController.BASE_PATH + "/import";
    private static final Path PROFILE_DIRECTORY = Path.of("ember", "Profiles", "Default");
    private static final EmberProfile PROFILE = new EmberProfile(
            PROFILE_DIRECTORY, PROFILE_DIRECTORY.resolve("MyVideos48.emm"), 48, Optional.empty());
    private static final EmberLibraryStatistics LIBRARY = new EmberLibraryStatistics(3, 1, 1, 2, 1, 1);

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private EmberImportService importService;

    @Test
    void returnsInspectionWithSettings() {
        var settings = new EmberSettings(1, "de-DE", List.of(".mkv"), List.of(".srt"),
                Optional.of(new EmberProxySettings("http://proxy.local", 3128, "proxyuser")), "de-DE", "de");
        when(importService.upload(List.of(DATABASE))).thenReturn(EmberInspectionResponse.from(
                new EmberProfileInspection(PROFILE, LIBRARY, Optional.of(settings)),
                List.of(new EmberMovieSource(6, "filme", "a:\\medien\\filme", "de-DE")),
                List.of(new EmberTvShowSource(2, "serien", "a:\\medien\\serien", "de-DE")),
                List.of(new EmberKodiHost("FireKodi", "firetv.local", 8080, "kodi", "geheim", true,
                        List.of(new EmberKodiHost.Source("Movie", "a:\\medien\\filme", "smb://nas/filme/"))))));

        assertThat(upload())
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {
                          "schemaVersion": 48,
                          "library": {"movies": 3, "movieSets": 1, "tvShows": 1, "episodes": 2,
                                      "movieSources": 1, "tvShowSources": 1},
                          "sources": [{"name": "filme", "path": "a:\\\\medien\\\\filme"}],
                          "tvSources": [{"name": "serien", "path": "a:\\\\medien\\\\serien"}],
                          "kodiHosts": [{"name": "FireKodi", "address": "firetv.local", "port": 8080,
                                         "pathMappings": 1}],
                          "settings": {
                            "language": "de-DE",
                            "videoExtensions": [".mkv"],
                            "subtitleExtensions": [".srt"],
                            "proxy": {"uri": "http://proxy.local", "port": 3128, "username": "proxyuser"}
                          }
                        }
                        """);
    }

    @Test
    void returnsInspectionWithoutSettings() {
        when(importService.upload(any())).thenReturn(EmberInspectionResponse.from(
                new EmberProfileInspection(PROFILE, LIBRARY, Optional.empty()), List.of(), List.of(), List.of()));

        assertThat(upload())
                .hasStatusOk()
                .bodyJson()
                .satisfies(json -> assertThat(json.getJson()).contains("\"settings\":null"))
                .extractingPath("$.profileDirectory").isEqualTo(PROFILE_DIRECTORY.toString());
    }

    @Test
    void answersInvalidProfileWithUnprocessableContent() {
        when(importService.upload(any())).thenThrow(new InvalidProfileException(
                LocalizedText.of("ember.databaseNotFound").with("directory", "ember")));

        assertThat(upload())
                .hasStatus(422)
                .bodyJson()
                .satisfies(body -> assertThat(body).extractingPath("$.detailText.key")
                        .isEqualTo("ember.databaseNotFound"))
                .extractingPath("$.detail")
                .isEqualTo("No Ember database (MyVideos*.emm) was found in the directory “ember”.");
    }

    @Test
    void answersAnUnusableUploadWithUnprocessableContent() {
        when(importService.upload(any())).thenThrow(
                new EmberProfileUploadException(LocalizedText.of("migration.upload.noDatabase")));

        assertThat(upload())
                .hasStatus(422)
                .bodyJson()
                .satisfies(body -> assertThat(body).extractingPath("$.titleText.key")
                        .isEqualTo("migration.invalidProfile.title"))
                .extractingPath("$.detailText.key").isEqualTo("migration.upload.noDatabase");
    }

    @Test
    void requiresFiles() {
        assertThat(mockMvc.post().uri(UPLOAD_PATH).with(csrf()).multipart()).hasStatus(400);
    }

    @Test
    void discardsTheUpload() {
        assertThat(mockMvc.delete().uri(UPLOAD_PATH).with(csrf())).hasStatus(204);

        verify(importService).discardUpload();
    }

    @Test
    @WithAnonymousUser
    void requiresAuthentication() {
        assertThat(upload()).hasStatus(401);
        assertThat(mockMvc.delete().uri(UPLOAD_PATH).with(csrf())).hasStatus(401);
    }

    @Test
    void startsImportAsJob() {
        UUID jobId = UUID.fromString("7d0f6c9e-1b57-4a8e-9a3f-0e6c7b0a1f02");
        var request = new EmberImportRequest(
                List.of(new EmberImportRequest.PathMapping("a:\\medien\\filme", "/media/filme")), true, true, true, true);
        when(importService.start(request)).thenReturn(new JobSnapshot(jobId, EmberImportService.JOB_KEY,
                LocalizedText.literal("Ember-Import (Probelauf)"), JobStatus.RUNNING, 0, 0, null, null, Instant.now(), null));

        assertThat(mockMvc.post().uri(IMPORT_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"pathMappings": [{"emberPath": "a:\\\\medien\\\\filme", "kmmPath": "/media/filme"}],
                 "importSettings": true, "importKodiHosts": true, "importTvShows": true, "dryRun": true}
                """))
                .hasStatus(202)
                .hasHeader("Location", "/api/v1/jobs/" + jobId);
    }

    @Test
    void rejectsIncompleteImportRequest() {
        assertThat(mockMvc.post().uri(IMPORT_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"pathMappings": [{"emberPath": "a:\\\\filme", "kmmPath": ""}]}
                """))
                .hasStatus(400);
        verifyNoInteractions(importService);
    }

    @Test
    void answersImportOfInvalidProfileWithUnprocessableContent() {
        when(importService.start(any())).thenThrow(new InvalidProfileException(LocalizedText.literal("x")));

        assertThat(mockMvc.post().uri(IMPORT_PATH).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                {"pathMappings": [], "importSettings": false, "importKodiHosts": false,
                 "importTvShows": false, "dryRun": false}
                """))
                .hasStatus(422);
    }

    private MvcTestResult upload() {
        return mockMvc.post().uri(UPLOAD_PATH).with(csrf()).multipart().file(DATABASE).exchange();
    }

    /** Makes the protected constructor accessible for test cases. */
    private static final class InvalidProfileException extends EmberProfileException {

        InvalidProfileException(LocalizedText message) {
            super(message);
        }
    }
}
