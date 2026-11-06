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
 * Copies the plugins shipped with KMM ({@code classpath:bundled-plugins/*.jar}) into the
 * plugin directory. This way delivery works the same in the fat JAR as in the Docker image.
 * A changed file (e.g. after an update) is replaced, an identical one is left untouched.
 */
@Slf4j
@RequiredArgsConstructor
class BundledPluginInstaller {

    static final String BUNDLED_PLUGINS = "classpath*:bundled-plugins/*.jar";

    private final ResourcePatternResolver resources;

    /**
     * @return number of newly installed or updated plugins
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
