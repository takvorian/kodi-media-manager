package de.tiktak.kmm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KodiMediaManagerApplicationTests {

    @Autowired
    private MockMvcTester mockMvc;

    @Test
    void servesSystemInfoWithEmbeddedDatabase() {
        assertThat(mockMvc.get().uri("/api/v1/system/info"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.database").isEqualTo("H2");
    }

    @Test
    void reportsHealthWithoutAuthentication() {
        assertThat(mockMvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void servesAngularAppForClientRoutes() {
        assertThat(mockMvc.get().uri("/movies/42").accept(MediaType.TEXT_HTML))
                .hasStatusOk()
                .bodyText().contains("<app-root>");
    }

    @Test
    void protectsApiDocumentation() {
        assertThat(mockMvc.get().uri("/v3/api-docs")).hasStatus(401);
    }
}
