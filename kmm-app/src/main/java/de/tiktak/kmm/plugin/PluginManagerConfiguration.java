package de.tiktak.kmm.plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration(proxyBeanMethods = false)
class PluginManagerConfiguration {

    @Bean
    PluginManager pluginManager(PluginProperties properties) throws IOException {
        Path directory = Files.createDirectories(properties.directory());
        log.info("Loading plugins from {}", directory.toAbsolutePath());
        return new DefaultPluginManager(directory);
    }

    @Bean
    PluginLifecycle pluginLifecycle(PluginManager pluginManager) {
        return new PluginLifecycle(pluginManager);
    }
}
