package de.tiktak.kmm.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = DatabaseResetController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
class DatabaseResetControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private DatabaseResetService service;

    @Test
    void clearsTheDatabaseAndKeepsTheSettingsOnRequest() {
        assertThat(post("{\"keepSettings\": true}")).hasStatus(HttpStatus.NO_CONTENT);

        verify(service).clear(true);
    }

    @Test
    void clearsTheSettingsOnlyWhenAskedExplicitly() {
        assertThat(post("{\"keepSettings\": false}")).hasStatus(HttpStatus.NO_CONTENT);

        verify(service).clear(false);
    }

    @Test
    void rejectsARequestWithoutTheChoiceAboutTheSettings() {
        assertThat(post("{}")).hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(service);
    }

    @Test
    void requiresAuthentication() {
        assertThat(mockMvc.post().uri(DatabaseResetController.CLEAR_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"keepSettings\": false}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        verifyNoInteractions(service);
    }

    private MvcTestResult post(String body) {
        return mockMvc.post().uri(DatabaseResetController.CLEAR_PATH).with(user("admin")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }
}
