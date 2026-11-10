package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.kodi.rpc.KodiVersion;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = KodiHostController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class KodiHostControllerTest {

    private static final KodiHostResponse HOST = new KodiHostResponse(3, "Wohnzimmer", "kodi.local", 8080, "kodi",
            true, true, true, List.of(new KodiHostCommand.PathMapping("/media/filme", "smb://nas/filme/")), null);
    private static final String VALID_HOST = """
            {"name": "Wohnzimmer", "address": "kodi.local", "port": 8080, "username": "kodi", "password": null,
             "realtimeSync": true, "pathMappings": [{"localPath": "/media/filme", "kodiPath": "smb://nas/filme/"}]}
            """;

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private KodiHostService hostService;

    @Test
    void listsHostsWithoutPasswords() {
        when(hostService.findAll()).thenReturn(List.of(HOST));

        assertThat(mockMvc.get().uri(KodiHostController.BASE_PATH)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"id": 3, "name": "Wohnzimmer", "passwordSet": true,
                  "pathMappings": [{"localPath": "/media/filme", "kodiPath": "smb://nas/filme/"}]}]
                """);
    }

    @Test
    void createsAHost() {
        when(hostService.create(any())).thenReturn(HOST);

        assertThat(mockMvc.post().uri(KodiHostController.BASE_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_HOST))
                .hasStatus(201)
                .hasHeader("Location", KodiHostController.BASE_PATH + "/3");
        verify(hostService).create(new KodiHostSettings("Wohnzimmer", "kodi.local", 8080, "kodi", null, true,
                List.of(new KodiPathMapping("/media/filme", "smb://nas/filme/"))));
    }

    @Test
    void updatesAHost() {
        when(hostService.update(eq(3L), any())).thenReturn(HOST);

        assertThat(mockMvc.put().uri(KodiHostController.BASE_PATH + "/3").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VALID_HOST))
                .hasStatusOk();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\": \"\", \"address\": \"kodi\", \"port\": 8080, \"pathMappings\": []}",
            "{\"name\": \"x\", \"address\": \"http://kodi\", \"port\": 8080, \"pathMappings\": []}",
            "{\"name\": \"x\", \"address\": \"kodi\", \"port\": 0, \"pathMappings\": []}",
            "{\"name\": \"x\", \"address\": \"kodi\", \"port\": 8080}",
            "{\"name\": \"x\", \"address\": \"kodi\", \"port\": 8080, \"pathMappings\": [{\"localPath\": \"/m\"}]}"
    })
    void rejectsInvalidHosts(String body) {
        assertThat(mockMvc.post().uri(KodiHostController.BASE_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(400);
        verifyNoInteractions(hostService);
    }

    @Test
    void switchesAHostOnAndOff() {
        when(hostService.switchTo(3, false)).thenReturn(HOST);

        assertThat(mockMvc.put().uri(KodiHostController.BASE_PATH + "/3/enabled").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"))
                .hasStatusOk();
        verify(hostService).switchTo(3, false);
    }

    @Test
    void deletesAHost() {
        assertThat(mockMvc.delete().uri(KodiHostController.BASE_PATH + "/3").with(csrf())).hasStatus(204);
        verify(hostService).delete(3);
    }

    @Test
    void testsTheConnection() {
        when(hostService.testConnection(3)).thenReturn(new KodiVersion("Kodi", 21, 2));

        assertThat(mockMvc.post().uri(KodiHostController.BASE_PATH + "/3/test").with(csrf()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("{\"version\": \"Kodi 21.2\", \"warning\": null}");
    }

    @Test
    void reportsPathMappingProblemsOfTheConnectionTest() {
        when(hostService.testConnection(3)).thenReturn(new KodiVersion("Kodi", 21, 2));
        when(hostService.pathMappingWarning(3)).thenReturn(Optional.of(LocalizedText.of("kodi.host.noSourceWarning")
                .with("paths", "a:\\serien").with("sources", LocalizedText.of("kodiRpc.noSources"))));

        assertThat(mockMvc.post().uri(KodiHostController.BASE_PATH + "/3/test").with(csrf()))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        {"version": "Kodi 21.2", "warning": {"key": "kodi.host.noSourceWarning",
                         "args": {"paths": "a:\\\\serien", "sources": {"key": "kodiRpc.noSources", "args": {}}}}}
                        """);
    }
}
