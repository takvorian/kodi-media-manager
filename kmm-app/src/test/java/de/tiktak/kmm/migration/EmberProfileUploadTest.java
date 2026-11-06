package de.tiktak.kmm.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class EmberProfileUploadTest {

    @TempDir
    Path home;

    private EmberProfileUpload upload;

    @BeforeEach
    void setUp() {
        upload = new EmberProfileUpload(home);
    }

    @Test
    void storesTheProfileFiles() throws IOException {
        upload.store(List.of(file("MyVideos48.emm"), file("Settings.xml"), file("AdvancedSettings.xml"),
                file("Interface.Kodi.xml")));

        assertThat(upload.directory()).isEqualTo(home.resolve(EmberProfileUpload.DIRECTORY_NAME));
        assertThat(upload.directory().resolve("Settings.xml")).hasContent("Settings.xml");
        try (var files = Files.list(upload.directory())) {
            assertThat(files).hasSize(4);
        }
    }

    @Test
    void replacesAnEarlierUpload() {
        upload.store(List.of(file("MyVideos48.emm"), file("Settings.xml")));

        upload.store(List.of(file("MyVideos47.emm")));

        assertThat(upload.directory().resolve("Settings.xml")).doesNotExist();
        assertThat(upload.directory().resolve("MyVideos48.emm")).doesNotExist();
        assertThat(upload.directory().resolve("MyVideos47.emm")).exists();
    }

    @Test
    void requiresADatabaseAndKeepsTheEarlierUpload() {
        upload.store(List.of(file("MyVideos48.emm")));

        assertThatThrownBy(() -> upload.store(List.of(file("Settings.xml"))))
                .isInstanceOf(EmberProfileUploadException.class).hasMessage("The uploaded files contain no Ember database.");

        assertThat(upload.directory().resolve("MyVideos48.emm")).exists();
    }

    @Test
    void rejectsUnexpectedAndEscapingNames() {
        for (String name : List.of("poster.jpg", "../MyVideos48.emm", "..\\Settings.xml", "sub/Settings.xml")) {
            assertThatThrownBy(() -> upload.store(List.of(file("MyVideos48.emm"), file(name))))
                    .as(name).isInstanceOf(EmberProfileUploadException.class).hasMessageContaining("does not belong");
        }
        assertThat(home.resolve("MyVideos48.emm")).doesNotExist();
    }

    @Test
    void rejectsAFileWithoutName() {
        MockMultipartFile unnamed = new MockMultipartFile("files", null, null, new byte[0]);

        assertThatThrownBy(() -> upload.store(List.of(unnamed))).isInstanceOf(EmberProfileUploadException.class);
    }

    @Test
    void asksForAnUploadWhenThereIsNone() {
        assertThatThrownBy(upload::directory)
                .isInstanceOf(EmberProfileUploadException.class).hasMessage("Please upload the Ember profile first.");
    }

    @Test
    void discardsTheUpload() {
        upload.store(List.of(file("MyVideos48.emm")));

        upload.discard();
        upload.discard();

        assertThat(home.resolve(EmberProfileUpload.DIRECTORY_NAME)).doesNotExist();
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("files", name, null, name.getBytes(StandardCharsets.UTF_8));
    }
}
