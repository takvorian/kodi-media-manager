package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;
import de.tiktak.kmm.trailer.TrailerOption;
import de.tiktak.kmm.trailer.TrailerOptions;

@WebMvcTest(controllers = MovieTrailerController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class MovieTrailerControllerTest {

    private static final UUID JOB_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String LINK = "plugin://plugin.video.youtube/?action=play_video&videoid=abc";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MovieTrailerService trailerService;

    @MockitoBean
    private MovieBatchService batchService;

    @Test
    void listsTheCandidates() {
        when(trailerService.candidates(7L)).thenReturn(new TrailerOptions(List.of(
                new TrailerOption("Trailer", LINK, URI.create("https://www.youtube.com/watch?v=abc"), null, "de",
                        "Trailer", true, 1080, "2020-01-01", "TMDB")), List.of("fanart.tv: Key fehlt")));

        assertThat(mockMvc.get().uri(MovieTrailerController.CANDIDATES_PATH, 7L))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"candidates": [{"name": "Trailer", "url": "%s", "language": "de", "official": true}],
                         "warnings": ["fanart.tv: Key fehlt"]}
                        """.formatted(LINK));
    }

    @Test
    void appliesAndRemovesTheTrailer() {
        when(trailerService.apply(7L, LINK)).thenReturn(details(LINK));
        when(trailerService.remove(7L)).thenReturn(details(null));

        assertThat(mockMvc.put().uri(MovieTrailerController.TRAILER_PATH, 7L).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"%s\"}".formatted(LINK)))
                .hasStatusOk()
                .bodyJson().extractingPath("$.trailer").isEqualTo(LINK);
        assertThat(mockMvc.delete().uri(MovieTrailerController.TRAILER_PATH, 7L).with(csrf()))
                .hasStatusOk()
                .bodyJson().extractingPath("$.trailer").isNull();
    }

    @Test
    void rejectsAnEmptyLink() {
        assertThat(mockMvc.put().uri(MovieTrailerController.TRAILER_PATH, 7L).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \" \"}"))
                .hasStatus4xxClientError();
        verify(trailerService, never()).apply(7L, " ");
    }

    @Test
    void startsTheJobForMissingTrailers() {
        when(batchService.startTrailerFill(List.of(1L, 2L))).thenReturn(new JobSnapshot(JOB_ID, "movies-batch",
                "Fehlende Trailer für 2 Filme holen", JobStatus.RUNNING, 0, 0, null, null,
                Instant.parse("2026-10-28T12:00:00Z"), null));

        assertThat(mockMvc.post().uri(MovieTrailerController.FILL_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"movieIds\": [1, 2]}"))
                .hasStatus(202)
                .hasHeader("Location", "/api/v1/jobs/" + JOB_ID)
                .bodyJson().extractingPath("$.title").isEqualTo("Fehlende Trailer für 2 Filme holen");
    }

    @Test
    void rejectsAJobWithoutMovies() {
        assertThat(mockMvc.post().uri(MovieTrailerController.FILL_PATH).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"movieIds\": []}"))
                .hasStatus4xxClientError();
        verify(batchService, never()).startTrailerFill(anyList());
    }

    private static MovieDetails details(String trailer) {
        return new MovieDetails(7L, 1L, "/filme/Heat.mkv", DiscFormat.FILE, 1, Instant.EPOCH,
                new MovieMetadata("Heat", null, null, 1995, null, null, null, null, null, null, List.of(), null, null,
                        null, null),
                null, null, null, trailer);
    }
}
