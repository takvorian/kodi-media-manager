package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.job.JobAlreadyRunningException;
import de.tiktak.kmm.job.JobSnapshot;
import de.tiktak.kmm.job.JobStatus;
import de.tiktak.kmm.scanner.DiscFormat;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = MovieController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class MovieControllerTest {

    private static final MovieSummary HEAT = new MovieSummary(
            5L, 1L, "Heat", 1995, "/media/Heat.mkv", DiscFormat.FILE, 1, Instant.parse("2026-10-25T10:00:00Z"));

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MovieQueryService queryService;

    @MockitoBean
    private MovieScanService scanService;

    @MockitoBean
    private MovieMetadataService metadataService;

    @Test
    void listsMoviesSortedByTitleWithDefaultPageSize() {
        when(queryService.findMovies(isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(HEAT), PageRequest.of(0, MovieController.DEFAULT_PAGE_SIZE), 1));

        assertThat(mockMvc.get().uri(MovieController.MOVIES_PATH))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"content": [{"id": 5, "title": "Heat", "year": 1995, "discFormat": "FILE", "partCount": 1}],
                         "page": 0, "size": 50, "totalElements": 1, "totalPages": 1}
                        """);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).findMovies(isNull(), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("title")).isNotNull()
                .extracting(Sort.Order::getDirection).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void passesSearchAndPagingParameters() {
        when(queryService.findMovies(eq("heat"), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(mockMvc.get().uri(MovieController.MOVIES_PATH)
                .param("search", "heat").param("page", "2").param("size", "20")).hasStatusOk();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).findMovies(eq("heat"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void answersUnknownSortPropertyWithBadRequest() {
        when(queryService.findMovies(any(), any())).thenThrow(
                new PropertyReferenceException("foo", TypeInformation.of(Movie.class), List.of()));

        assertThat(mockMvc.get().uri(MovieController.MOVIES_PATH).param("sort", "foo"))
                .hasStatus(400)
                .bodyJson()
                .extractingPath("$.title").isEqualTo(MovieController.INVALID_SORT_TITLE);
    }

    @Test
    void startsScanAsJob() {
        UUID jobId = UUID.fromString("7d0f6c9e-1b57-4a8e-9a3f-0e6c7b0a1f00");
        when(scanService.startScan(1L)).thenReturn(new JobSnapshot(jobId, "movie-scan:1", "Scan „Filme“",
                JobStatus.RUNNING, 0, 0, null, null, Instant.parse("2026-10-25T12:00:00Z"), null));

        assertThat(mockMvc.post().uri(MovieController.SCAN_PATH, 1L).with(csrf()))
                .hasStatus(202)
                .hasHeader("Location", "/api/v1/jobs/" + jobId)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id": "7d0f6c9e-1b57-4a8e-9a3f-0e6c7b0a1f00", "key": "movie-scan:1", "status": "RUNNING"}
                        """);
    }

    @Test
    void answersSecondScanOfSameSourceWithConflict() {
        JobSnapshot running = new JobSnapshot(UUID.randomUUID(), "movie-scan:1", "Scan „Filme“", JobStatus.RUNNING,
                0, 0, null, null, Instant.parse("2026-10-25T12:00:00Z"), null);
        when(scanService.startScan(1L)).thenThrow(new JobAlreadyRunningException(running));

        assertThat(mockMvc.post().uri(MovieController.SCAN_PATH, 1L).with(csrf()))
                .hasStatus(409)
                .bodyJson()
                .extractingPath("$.detail").isEqualTo("Scan „Filme“ läuft bereits.");
    }

    @Test
    void returnsMovieDetails() {
        when(metadataService.findDetails(5L)).thenReturn(details());

        assertThat(mockMvc.get().uri(MovieController.MOVIE_PATH, 5L))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"id": 5, "metadata": {"title": "Heat", "year": 1995, "genres": ["Krimi"], "imdbId": "tt0113277"},
                         "nfo": {"path": "/media/Heat.nfo", "exists": true}}
                        """);
    }

    @Test
    void answersUnknownMovieWithNotFound() {
        when(metadataService.findDetails(9L)).thenThrow(new MovieNotFoundException(9L));

        assertThat(mockMvc.get().uri(MovieController.MOVIE_PATH, 9L)).hasStatus(404);
    }

    @Test
    void updatesMetadata() {
        when(metadataService.update(eq(5L), any())).thenReturn(details());

        assertThat(mockMvc.put().uri(MovieController.METADATA_PATH, 5L).with(csrf())
                .contentType("application/json")
                .content("""
                        {"title": " Heat ", "year": 1995, "premiered": "1995-12-15", "genres": ["Krimi"],
                         "imdbId": "tt0113277"}
                        """))
                .hasStatusOk();

        ArgumentCaptor<MovieMetadata> metadata = ArgumentCaptor.forClass(MovieMetadata.class);
        verify(metadataService).update(eq(5L), metadata.capture());
        assertThat(metadata.getValue().title()).isEqualTo("Heat");
        assertThat(metadata.getValue().premiered()).isEqualTo(LocalDate.of(1995, 12, 15));
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThat(mockMvc.put().uri(MovieController.METADATA_PATH, 5L).with(csrf())
                .contentType("application/json")
                .content("""
                        {"title": "", "genres": [], "imdbId": "falsch"}
                        """))
                .hasStatus(400);
        verifyNoInteractions(metadataService);
    }

    private static MovieDetails details() {
        var metadata = new MovieMetadata("Heat", null, null, 1995, null, null, null, null, null, null,
                List.of("Krimi"), "tt0113277", null);
        return new MovieDetails(5L, 1L, "/media/Heat.mkv", DiscFormat.FILE, 1, Instant.parse("2026-10-25T10:00:00Z"),
                metadata, new MovieDetails.NfoInfo("/media/Heat.nfo", true, Instant.parse("2026-10-25T11:00:00Z")));
    }

    @Test
    @WithAnonymousUser
    void requiresAuthenticationForScan() {
        assertThat(mockMvc.post().uri(MovieController.SCAN_PATH, 1L).with(csrf())).hasStatus(401);
    }
}
