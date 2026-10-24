package de.tiktak.kmm.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

class SpaFallbackResourceResolverTest {

    private final SpaFallbackResourceResolver resolver = new SpaFallbackResourceResolver();

    @TempDir
    Path staticDirectory;

    private Resource location;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(staticDirectory.resolve(SpaFallbackResourceResolver.INDEX_FILE), "<app-root></app-root>");
        Files.writeString(staticDirectory.resolve("main.js"), "console.log('kmm')");
        location = new FileSystemResource(staticDirectory.toString() + "/");
    }

    @Test
    void servesExistingFile() throws IOException {
        assertThat(resolver.getResource("main.js", location).getFilename()).isEqualTo("main.js");
    }

    @ParameterizedTest
    @ValueSource(strings = {"movies", "movies/42", "settings/plugins"})
    void servesIndexForClientRoutes(String route) throws IOException {
        assertThat(resolver.getResource(route, location).getFilename()).isEqualTo(SpaFallbackResourceResolver.INDEX_FILE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"api/v1/unknown", "actuator/unknown", "v3/api-docs", "swagger-ui/index"})
    void doesNotServeIndexForBackendPaths(String path) throws IOException {
        assertThat(resolver.getResource(path, location)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing.js", "assets/missing.png"})
    void doesNotServeIndexForMissingFiles(String path) throws IOException {
        assertThat(resolver.getResource(path, location)).isNull();
    }
}
