package de.tiktak.kmm.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = MediaSourceController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class MediaSourceControllerTest {

    private static final String JSON = "application/json";
    private static final Instant CREATED_AT = Instant.parse("2026-10-25T10:00:00Z");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MediaSourceService sourceService;

    @Test
    void listsSources() {
        MediaSource source = source(1L, "Filme", "/media/filme");
        when(sourceService.findAll()).thenReturn(List.of(source));

        assertThat(mockMvc.get().uri(MediaSourceController.BASE_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id": 1, "name": "Filme", "path": "/media/filme", "mediaType": "MOVIE",
                          "createdAt": "2026-10-25T10:00:00Z", "lastScannedAt": null}]
                        """);
    }

    @Test
    void changesTheLanguageOfASource() {
        MediaSource source = source(7L, "Filme", "/media/filme");
        when(source.getMetadataLanguage()).thenReturn("fr-FR");
        when(sourceService.changeLanguage(7, "fr-FR")).thenReturn(source);

        assertThat(mockMvc.put().uri(MediaSourceController.BASE_PATH + "/7/language").with(csrf())
                .contentType(JSON).content("{\"language\": \"fr-FR\"}"))
                .hasStatusOk().bodyJson().extractingPath("$.metadataLanguage").isEqualTo("fr-FR");
    }

    @Test
    void clearsTheLanguageOfASource() {
        MediaSource source = source(7L, "Filme", "/media/filme");
        when(sourceService.changeLanguage(7, null)).thenReturn(source);

        assertThat(mockMvc.put().uri(MediaSourceController.BASE_PATH + "/7/language").with(csrf())
                .contentType(JSON).content("{\"language\": null}")).hasStatusOk();
    }

    @Test
    void rejectsAnInvalidLanguage() {
        assertThat(mockMvc.put().uri(MediaSourceController.BASE_PATH + "/7/language").with(csrf())
                .contentType(JSON).content("{\"language\": \"Deutsch\"}")).hasStatus(400);
    }

    @Test
    void createsSourceAndReturnsLocation() {
        var command = new CreateMediaSourceCommand("Filme", "/media/filme", MediaType.MOVIE);
        MediaSource source = source(7L, "Filme", "/media/filme");
        when(sourceService.create(command)).thenReturn(source);

        assertThat(mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf())
                .contentType(JSON)
                .content("""
                        {"name": "Filme", "path": "/media/filme", "mediaType": "MOVIE"}
                        """))
                .hasStatus(201)
                .hasHeader("Location", MediaSourceController.BASE_PATH + "/7")
                .bodyJson()
                .extractingPath("$.id").isEqualTo(7);
    }

    @Test
    void rejectsInvalidRequestWithoutCallingService() {
        assertThat(mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf())
                .contentType(JSON)
                .content("""
                        {"name": " ", "path": "", "mediaType": null}
                        """))
                .hasStatus(400);
        verifyNoInteractions(sourceService);
    }

    @Test
    void answersConflictWithProblemDetail() {
        when(sourceService.create(any()))
                .thenThrow(MediaSourceConflictException.nameTaken("Filme"));

        assertThat(mockMvc.post().uri(MediaSourceController.BASE_PATH).with(csrf())
                .contentType(JSON)
                .content("""
                        {"name": "Filme", "path": "/media/filme", "mediaType": "MOVIE"}
                        """))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.detailText.args.name").isEqualTo("Filme");
    }

    @Test
    void deletesSource() {
        assertThat(mockMvc.delete().uri(MediaSourceController.BASE_PATH + "/3").with(csrf())).hasStatus(204);
        verify(sourceService).delete(3L);
    }

    @Test
    void answersDeletingUnknownSourceWithNotFound() {
        doThrow(new MediaSourceNotFoundException(9L)).when(sourceService).delete(9L);

        assertThat(mockMvc.delete().uri(MediaSourceController.BASE_PATH + "/9").with(csrf()))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.titleText.key").isEqualTo("source.notFound.title");
    }

    private static MediaSource source(long id, String name, String path) {
        MediaSource source = mock(MediaSource.class);
        when(source.getId()).thenReturn(id);
        when(source.getName()).thenReturn(name);
        when(source.getPath()).thenReturn(path);
        when(source.getMediaType()).thenReturn(MediaType.MOVIE);
        when(source.getCreatedAt()).thenReturn(CREATED_AT);
        return source;
    }
}
