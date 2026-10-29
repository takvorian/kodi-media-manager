package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

class BundledPluginInstallerTest {

    @TempDir
    Path pluginDirectory;

    @Test
    void installsPluginsShippedOnTheClasspath() {
        var installer = new BundledPluginInstaller(new PathMatchingResourcePatternResolver());

        int installed = installer.installInto(pluginDirectory);

        assertThat(installed).isEqualTo(6);
        assertThat(pluginDirectory.resolve("kmm-plugin-tmdb.jar")).isRegularFile();
        assertThat(pluginDirectory.resolve("kmm-plugin-fanarttv.jar")).isRegularFile();
        assertThat(pluginDirectory.resolve("kmm-plugin-tvdb.jar")).isRegularFile();
        assertThat(pluginDirectory.resolve("kmm-plugin-televisiontunes.jar")).isRegularFile();
        assertThat(pluginDirectory.resolve("kmm-plugin-youtube.jar")).isRegularFile();
        assertThat(pluginDirectory.resolve("kmm-plugin-trakt.jar")).isRegularFile();
    }

    @Test
    void leavesIdenticalPluginUntouched() throws IOException {
        var installer = installerFor(plugin("kmm-plugin-x.jar", "v1"));
        installer.installInto(pluginDirectory);
        var modified = Files.getLastModifiedTime(pluginDirectory.resolve("kmm-plugin-x.jar"));

        assertThat(installer.installInto(pluginDirectory)).isZero();
        assertThat(Files.getLastModifiedTime(pluginDirectory.resolve("kmm-plugin-x.jar"))).isEqualTo(modified);
    }

    @Test
    void replacesOutdatedPlugin() throws IOException {
        Files.writeString(pluginDirectory.resolve("kmm-plugin-x.jar"), "v1");

        assertThat(installerFor(plugin("kmm-plugin-x.jar", "v2")).installInto(pluginDirectory)).isEqualTo(1);
        assertThat(Files.readString(pluginDirectory.resolve("kmm-plugin-x.jar"))).isEqualTo("v2");
        try (var files = Files.list(pluginDirectory)) {
            assertThat(files).hasSize(1);
        }
    }

    @Test
    void reportsUnwritablePluginDirectory() {
        Path missing = pluginDirectory.resolve("missing");

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> installerFor(plugin("kmm-plugin-x.jar", "v1")).installInto(missing));
    }

    @Test
    void reportsUnreadableClasspath() throws IOException {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources(BundledPluginInstaller.BUNDLED_PLUGINS)).thenThrow(new IOException("kaputt"));

        assertThatExceptionOfType(UncheckedIOException.class)
                .isThrownBy(() -> new BundledPluginInstaller(resolver).installInto(pluginDirectory));
    }

    private static BundledPluginInstaller installerFor(Resource... plugins) {
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        try {
            when(resolver.getResources(BundledPluginInstaller.BUNDLED_PLUGINS)).thenReturn(plugins);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new BundledPluginInstaller(resolver);
    }

    private static Resource plugin(String fileName, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }
}
