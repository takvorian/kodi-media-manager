package de.tiktak.kmm.kodi;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiLibraryScheduler;

/**
 * Wires the framework-free Kodi client ({@code kmm-kodi}). Kodi runs in the local network and is therefore contacted
 * without the proxy from the settings.
 */
@Configuration(proxyBeanMethods = false)
class KodiConfiguration {

    @Bean
    KodiClient kodiClient(KodiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .proxy(HttpClient.Builder.NO_PROXY)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new KodiClient(httpClient, properties.requestTimeout());
    }

    @Bean
    KodiLibraryScheduler kodiLibraryScheduler(KodiClient kodiClient, KodiProperties properties) {
        return new KodiLibraryScheduler(kodiClient, properties.scanPollInterval());
    }
}
