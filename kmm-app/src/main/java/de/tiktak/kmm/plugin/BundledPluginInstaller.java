package de.tiktak.kmm.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kopiert die mit KMM ausgelieferten Plugins ({@code classpath:bundled-plugins/*.jar}) in das
 * Plugin-Verzeichnis. So funktioniert die Auslieferung im Fat-JAR wie im Docker-Image gleich.
 * Eine geänderte Datei (z. B. nach einem Update) wird ersetzt, eine gleiche bleibt unberührt.
 */
@Slf4j
@RequiredArgsConstructor
class BundledPluginInstaller {

    static final String BUNDLED_PLUGINS = "classpath*:bundled-plugins/*.jar";

    private final ResourcePatternResolver resources;

    /**
     * @return Anzahl der neu installierten oder aktualisierten Plugins
     */
    int installInto(Path pluginDirectory) {
        try {
            Resource[] bundled = resources.getResources(BUNDLED_PLUGINS);
            return (int) Arrays.stream(bundled).filter(plugin -> install(plugin, pluginDirectory)).count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean install(Resource plugin, Path pluginDirectory) {
        Path target = pluginDirectory.resolve(plugin.getFilename());
        try {
            byte[] content = read(plugin);
            if (Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), content)) {
                return false;
            }
            Path temporary = Files.createTempFile(pluginDirectory, ".install-", ".tmp");
            Files.write(temporary, content);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Installed bundled plugin {}", target.getFileName());
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Bundled plugin %s could not be installed".formatted(target), e);
        }
    }

    private static byte[] read(Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            return input.readAllBytes();
        }
    }
}
