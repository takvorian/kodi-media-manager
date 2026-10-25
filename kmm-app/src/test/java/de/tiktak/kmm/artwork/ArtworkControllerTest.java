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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.security.WebSecurityTestConfiguration;

@WebMvcTest(controllers = ArtworkController.class, properties = "kmm.security.enabled=true")
@Import(WebSecurityTestConfiguration.class)
@WithMockUser
class ArtworkControllerTest {

    private static final Instant MODIFIED = Instant.parse("2026-10-25T12:00:00Z");

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private ArtworkService artworkService;

    @TempDir
    Path directory;

    @Test
    void listsArtwork() {
        when(artworkService.artwork(5L)).thenReturn(List.of(
                new ArtworkInfo(ImageType.POSTER, true, "/media/Heat-poster.jpg", MODIFIED),
                new ArtworkInfo(ImageType.FANART, false, null, null)));

        assertThat(mockMvc.get().uri(ArtworkController.ARTWORK_PATH, 5L))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"type": "POSTER", "exists": true, "path": "/media/Heat-poster.jpg",
                          "modifiedAt": "2026-10-25T12:00:00Z"},
                         {"type": "FANART", "exists": false}]
                        """);
    }

    @Test
    void servesImageFileWithRevalidation() throws IOException {
        Path poster = Files.write(directory.resolve("Heat-poster.png"), new byte[] {1, 2, 3});
        when(artworkService.image(5L, ImageType.POSTER)).thenReturn(new ArtworkFile(ImageType.POSTER, poster, MODIFIED));

        assertThat(mockMvc.get().uri(ArtworkController.IMAGE_PATH, 5L, "POSTER"))
                .hasStatusOk()
                .hasContentType("image/png")
                .hasHeader("Cache-Control", "no-cache")
                .hasBodyTextEqualTo(new String(new byte[] {1, 2, 3}));
    }

    @Test
    void answersMissingImageWithNotFound() {
        when(artworkService.image(5L, ImageType.BANNER)).thenThrow(ArtworkProblemException.imageMissing(ImageType.BANNER));

        assertThat(mockMvc.get().uri(ArtworkController.IMAGE_PATH, 5L, "BANNER")).hasStatus(404);
    }

    @Test
    void listsCandidates() {
        when(artworkService.candidates(5L, ImageType.POSTER)).thenReturn(new ArtworkCandidates(List.of(
                new ArtworkCandidate("tmdb", "TMDB", ImageType.POSTER, URI.create("https://img/p.jpg"),
                        URI.create("https://img/w342/p.jpg"), 1000, 1500, "de", 5.4)),
                List.of("Für fanart.tv fehlt ein API-Key.")));

        assertThat(mockMvc.get().uri(ArtworkController.CANDIDATES_PATH, 5L, "POSTER"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"candidates": [{"scraperId": "tmdb", "previewUrl": "https://img/w342/p.jpg", "language": "de"}],
                         "warnings": ["Für fanart.tv fehlt ein API-Key."]}
                        """);
    }

    @Test
    void appliesImage() {
        when(artworkService.apply(5L, ImageType.FANART, "https://img/f.jpg"))
                .thenReturn(new ArtworkInfo(ImageType.FANART, true, "/media/Heat-fanart.jpg", MODIFIED));

        assertThat(mockMvc.put().uri(ArtworkController.TYPE_PATH, 5L, "FANART").with(csrf())
                .contentType("application/json").content("""
                        {"url": "https://img/f.jpg"}
                        """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.path").isEqualTo("/media/Heat-fanart.jpg");
    }

    @Test
    void rejectsMissingUrl() {
        assertThat(mockMvc.put().uri(ArtworkController.TYPE_PATH, 5L, "FANART").with(csrf())
                .contentType("application/json").content("{}"))
                .hasStatus(400);
    }

    @Test
    void removesImage() {
        assertThat(mockMvc.delete().uri(ArtworkController.TYPE_PATH, 5L, "CLEARART").with(csrf())).hasStatus(204);
        verify(artworkService).remove(5L, ImageType.CLEARART);
    }

    @Test
    void rejectsUnknownArtworkType() {
        assertThat(mockMvc.get().uri(ArtworkController.IMAGE_PATH, 5L, "THUMBNAIL")).hasStatus(400);
    }
}
