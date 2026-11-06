package de.tiktak.kmm.movie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.plugin.api.CollectionReference;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = MovieScrapeController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class MovieScrapeControllerTest {

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MovieScrapeService scrapeService;

    @Test
    void searchesWithOptionalTitleAndYear() {
        when(scrapeService.search(5L, "tmdb", "Heat", 1995))
                .thenReturn(List.of(new ScrapeCandidate("949", "Heat", "Heat", 1995, null, null)));

        assertThat(mockMvc.get().uri(MovieScrapeController.SEARCH_PATH, 5L)
                .param("scraper", "tmdb").param("title", "Heat").param("year", "1995"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"providerId": "949", "title": "Heat", "year": 1995}]
                        """);
    }

    @Test
    void returnsProposal() {
        when(scrapeService.proposal(5L, "tmdb", "949")).thenReturn(new MovieScrapeProposal(new MovieMetadata("Heat",
                null, null, 1995, null, null, null, null, 170, "FSK 16", List.of("Krimi"), "tt0113277", "949", null,
                null), new CollectionReference("1", "Heat-Reihe")));

        assertThat(mockMvc.get().uri(MovieScrapeController.PROPOSAL_PATH, 5L)
                .param("scraper", "tmdb").param("providerId", "949"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"metadata": {"title": "Heat", "runtime": 170, "mpaa": "FSK 16", "tmdbId": "949"},
                         "collection": {"providerId": "1", "name": "Heat-Reihe"}}
                        """);
    }

    @Test
    void assignsTheCollectionOfAScrapedMovie() {
        when(scrapeService.assignCollection(5L, new CollectionReference("2344", "Matrix Filmreihe"))).thenReturn(true);

        assertThat(mockMvc.post().uri(MovieScrapeController.COLLECTION_PATH, 5L).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerId\": \"2344\", \"name\": \"Matrix Filmreihe\"}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"assigned\": true}");
        assertThat(mockMvc.post().uri(MovieScrapeController.COLLECTION_PATH, 5L).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"providerId\": \"x\", \"name\": \"M\"}"))
                .hasStatus(400);
    }

    @Test
    void answersUnknownScraperWithNotFound() {
        when(scrapeService.search(5L, "tmdb", null, null)).thenThrow(ScraperProblemException.unknown("tmdb"));

        assertThat(mockMvc.get().uri(MovieScrapeController.SEARCH_PATH, 5L).param("scraper", "tmdb"))
                .hasStatus(404)
                .bodyJson()
                .extractingPath("$.title").isEqualTo("Scraper not found");
    }

    @Test
    void requiresScraperParameter() {
        assertThat(mockMvc.get().uri(MovieScrapeController.SEARCH_PATH, 5L)).hasStatus(400);
    }
}
