package de.tiktak.kmm.plugin;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.pf4j.PluginManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration(proxyBeanMethods = false)
class PluginManagerConfiguration {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Gemeinsamer HTTP-Client für Plugins. */
    @Bean
    HttpClient pluginHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    PluginManager pluginManager(PluginProperties properties, HttpClient pluginHttpClient,
            ObjectProvider<BuildProperties> buildProperties) throws IOException {
        Path directory = Files.createDirectories(properties.directory());
        log.info("Loading plugins from {}", directory.toAbsolutePath());
        return new KmmPluginManager(directory, new PluginContextFactory(properties, pluginHttpClient, buildProperties));
    }

    @Bean
    PluginLifecycle pluginLifecycle(PluginManager pluginManager, PluginProperties properties,
            ResourcePatternResolver resources) {
        return new PluginLifecycle(pluginManager, properties, new BundledPluginInstaller(resources));
    }
}
