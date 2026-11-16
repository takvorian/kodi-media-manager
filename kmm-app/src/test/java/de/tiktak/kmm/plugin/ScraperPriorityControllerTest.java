package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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
import de.tiktak.kmm.source.MediaSource;
import de.tiktak.kmm.source.MediaSourceService;

@WebMvcTest(controllers = ScraperPriorityController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class ScraperPriorityControllerTest {

    private static final String JSON = "application/json";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private ScraperPriorityService priorityService;

    @MockitoBean
    private MediaSourceService sourceService;

    @Test
    void showsTheOrdersFromTheSettings() {
        when(priorityService.global()).thenReturn(List.of(new ScraperPriorityView(ScraperKind.METADATA,
                MediaType.TV_SHOW, false, List.of(new ScraperPriorityView.Entry("tvdb", "TheTVDB", true, null)))));

        assertThat(mockMvc.get().uri(ScraperPriorityController.GLOBAL_PATH))
                .hasStatusOk().bodyJson().isLenientlyEqualTo("""
                        [{"kind": "METADATA", "mediaType": "TV_SHOW", "inherited": false,
                          "entries": [{"scraperId": "tvdb", "displayName": "TheTVDB", "enabled": true}]}]
                        """);
    }

    @Test
    void changesTheOrdersOfASourceOfItsMediaType() {
        MediaSource source = mock(MediaSource.class);
        when(source.getMediaType()).thenReturn(MediaType.TV_SHOW);
        when(sourceService.require(4L)).thenReturn(source);

        assertThat(mockMvc.put().uri(ScraperPriorityController.SOURCE_PATH, 4L).with(csrf()).contentType(JSON)
                .content("""
                        [{"kind": "METADATA", "mediaType": "TV_SHOW", "inherit": false,
                          "entries": [{"scraperId": "tmdb", "enabled": true}, {"scraperId": "tvdb", "enabled": false}]}]
                        """))
                .hasStatusOk();
        verify(priorityService).changeForSource(4L, MediaType.TV_SHOW, List.of(new ScraperPriorityChange(
                ScraperKind.METADATA, MediaType.TV_SHOW, false, List.of(
                        new ScraperPriorityChange.Entry("tmdb", true),
                        new ScraperPriorityChange.Entry("tvdb", false)))));
    }

    @Test
    void rejectsInvalidScraperIds() {
        assertThat(mockMvc.put().uri(ScraperPriorityController.GLOBAL_PATH).with(csrf()).contentType(JSON)
                .content("""
                        [{"kind": "ARTWORK", "mediaType": "MOVIE", "inherit": false,
                          "entries": [{"scraperId": "-tmdb", "enabled": true}]}]
                        """))
                .hasStatus(400);
        verifyNoInteractions(priorityService);
    }

    @Test
    void refusesAnUnknownKind() {
        assertThat(mockMvc.put().uri(ScraperPriorityController.GLOBAL_PATH).with(csrf()).contentType(JSON)
                .content("""
                        [{"kind": "SUBTITLES", "mediaType": "MOVIE", "entries": []}]
                        """))
                .hasStatus(400);
        verifyNoInteractions(priorityService);
    }
}
