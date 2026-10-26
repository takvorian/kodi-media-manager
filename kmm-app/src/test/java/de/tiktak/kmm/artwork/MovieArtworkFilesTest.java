package de.tiktak.kmm.artwork;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import de.tiktak.kmm.plugin.api.ImageType;
import de.tiktak.kmm.scanner.CompanionFiles;
import de.tiktak.kmm.scanner.DiscFormat;

class MovieArtworkFilesTest {

    private final MovieArtworkFiles artworkFiles = new MovieArtworkFiles();

    @TempDir
    Path directory;

    @Test
    void findsNothingWithoutImage() {
        assertThat(artworkFiles.find(files(), ImageType.POSTER)).isEmpty();
    }

    @Test
    void findsExistingImageInAnySupportedFormat() throws IOException {
        Files.writeString(directory.resolve("Heat-clearlogo.png"), "png");

        assertThat(artworkFiles.find(files(), ImageType.CLEARLOGO)).hasValueSatisfying(file -> {
            assertThat(file.file()).isEqualTo(directory.resolve("Heat-clearlogo.png"));
            assertThat(file.mediaType()).isEqualTo(MediaType.IMAGE_PNG);
            assertThat(file.modifiedAt()).isNotNull();
        });
    }

    @Test
    void savesImageAndReplacesVariantWithOtherExtension() throws IOException {
        Files.writeString(directory.resolve("Heat-poster.png"), "alt");

        ArtworkFile saved = artworkFiles.save(files(), ImageType.POSTER, image("neu", "jpg"));

        assertThat(saved.file()).isEqualTo(directory.resolve("Heat-poster.jpg"));
        assertThat(saved.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(Files.readString(saved.file())).isEqualTo("neu");
        assertThat(directory.resolve("Heat-poster.png")).doesNotExist();
        try (var entries = Files.list(directory)) {
            assertThat(entries).extracting(file -> file.getFileName().toString()).containsExactly("Heat-poster.jpg");
        }
    }

    @Test
    void findsAndReplacesStackArtworkNamedAfterTheFirstPart() throws IOException {
        Files.writeString(directory.resolve("Heat Part 1-poster.jpg"), "ember");
        CompanionFiles stack = CompanionFiles.of(directory.resolve("Heat Part 1.mkv"), DiscFormat.FILE, 2);

        assertThat(artworkFiles.find(stack, ImageType.POSTER)).hasValueSatisfying(
                file -> assertThat(file.file()).isEqualTo(directory.resolve("Heat Part 1-poster.jpg")));
        ArtworkFile saved = artworkFiles.save(stack, ImageType.POSTER, image("neu", "jpg"));

        assertThat(saved.file()).isEqualTo(directory.resolve("Heat-poster.jpg"));
        assertThat(directory.resolve("Heat Part 1-poster.jpg")).doesNotExist();
    }

    @Test
    void savesDiscArtworkAsPlainNameInMovieDirectory() {
        Path videoTs = directory.resolve("VIDEO_TS").resolve("VIDEO_TS.IFO");
        CompanionFiles dvd = CompanionFiles.of(videoTs, DiscFormat.DVD, 1);

        assertThat(artworkFiles.save(dvd, ImageType.FANART, image("bild", "jpg")).file())
                .isEqualTo(directory.resolve("fanart.jpg"));
    }

    @Test
    void deletesAllVariants() throws IOException {
        Files.writeString(directory.resolve("Heat-banner.jpg"), "a");
        Files.writeString(directory.resolve("Heat-banner.png"), "b");

        artworkFiles.delete(files(), ImageType.BANNER);

        assertThat(artworkFiles.find(files(), ImageType.BANNER)).isEmpty();
    }

    @Test
    void reportsUnwritableDirectory() {
        CompanionFiles missing = CompanionFiles.of(directory.resolve("fehlt").resolve("Heat.mkv"), DiscFormat.FILE, 1);

        assertThatThrownBy(() -> artworkFiles.save(missing, ImageType.POSTER, image("x", "jpg")))
                .isInstanceOfSatisfying(ArtworkProblemException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.getBody().getDetail()).contains("Schreibrechte");
                });
    }

    private CompanionFiles files() {
        return CompanionFiles.of(directory.resolve("Heat.mkv"), DiscFormat.FILE, 1);
    }

    private static DownloadedImage image(String content, String extension) {
        return new DownloadedImage(content.getBytes(StandardCharsets.UTF_8), extension);
    }
}
