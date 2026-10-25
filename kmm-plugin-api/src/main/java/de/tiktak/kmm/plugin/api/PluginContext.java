package de.tiktak.kmm.plugin.api;

import java.net.http.HttpClient;
import java.util.Optional;

/**
 * Dienste, die KMM einem Plugin bereitstellt. Plugins sehen bewusst keinen Spring-Kontext,
 * sondern nur diese schmale Schnittstelle.
 */
public interface PluginContext {

    /**
     * Einstellung des Plugins, z. B. {@code api-key} oder {@code language}. Konfiguriert über
     * {@code kmm.plugins.settings.<plugin-id>.<name>}.
     */
    Optional<String> setting(String name);

    /** Gemeinsamer HTTP-Client der Anwendung (Timeouts, später auch Proxy). */
    HttpClient httpClient();

    /** User-Agent, mit dem Plugins sich bei Anbietern melden. */
    String userAgent();
}
