package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = MovieSetArtworkController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class MovieSetArtworkControllerTest {

    private static final Instant MODIFIED = Instant.parse("2026-10-28T12:00:00Z");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private MovieSetArtworkService artworkService;

    @TempDir
    Path directory;

    @Test
    void listsArtwork() {
        when(artworkService.artwork(7L)).thenReturn(List.of(
                new ArtworkInfo(ImageType.POSTER, true, "/sets/Matrix-poster.jpg", MODIFIED),
                new ArtworkInfo(ImageType.KEYART, false, null, null)));

        assertThat(mockMvc.get().uri(MovieSetArtworkController.ARTWORK_PATH, 7L))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"type": "POSTER", "exists": true, "path": "/sets/Matrix-poster.jpg"},
                         {"type": "KEYART", "exists": false}]
                        """);
    }

    @Test
    void servesTheImageFile() throws IOException {
        Path poster = Files.write(directory.resolve("Matrix-poster.jpg"), new byte[] {1, 2});
        when(artworkService.image(7L, ImageType.POSTER)).thenReturn(new ArtworkFile(ImageType.POSTER, poster, MODIFIED));

        assertThat(mockMvc.get().uri(MovieSetArtworkController.IMAGE_PATH, 7L, "POSTER"))
                .hasStatusOk()
                .hasContentType("image/jpeg")
                .hasHeader("Cache-Control", "no-cache");
    }

    @Test
    void listsCandidates() {
        when(artworkService.candidates(7L, ImageType.FANART)).thenReturn(new ArtworkCandidates(List.of(
                new ArtworkCandidate("tmdb", "TMDB", ImageType.FANART, URI.create("https://img/f.jpg"), null, null,
                        null, null, null, null)), List.of()));

        assertThat(mockMvc.get().uri(MovieSetArtworkController.CANDIDATES_PATH, 7L, "FANART"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.candidates[0].url").isEqualTo("https://img/f.jpg");
    }

    @Test
    void appliesAnImage() {
        when(artworkService.apply(7L, ImageType.POSTER, "https://img/p.jpg"))
                .thenReturn(new ArtworkInfo(ImageType.POSTER, true, "/sets/Matrix-poster.jpg", MODIFIED));

        assertThat(mockMvc.put().uri(MovieSetArtworkController.TYPE_PATH, 7L, "POSTER").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"https://img/p.jpg\"}"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.exists").isEqualTo(true);
    }

    @Test
    void rejectsAnApplyWithoutUrl() {
        assertThat(mockMvc.put().uri(MovieSetArtworkController.TYPE_PATH, 7L, "POSTER").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \" \"}"))
                .hasStatus(400);
    }

    @Test
    void fillsMissingImages() {
        when(artworkService.fillMissing(7L)).thenReturn(new ArtworkFillResult(2, List.of("fanart.tv: aus")));

        assertThat(mockMvc.post().uri(MovieSetArtworkController.FILL_PATH, 7L).with(csrf()))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"saved": 2, "warnings": ["fanart.tv: aus"]}
                        """);
    }

    @Test
    void removesAnImage() {
        assertThat(mockMvc.delete().uri(MovieSetArtworkController.TYPE_PATH, 7L, "BANNER").with(csrf()))
                .hasStatus(204);
        verify(artworkService).remove(7L, ImageType.BANNER);
    }
}
