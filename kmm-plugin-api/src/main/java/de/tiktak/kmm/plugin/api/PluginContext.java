package de.tiktak.kmm.plugin.api;

import java.net.http.HttpClient;
import java.util.Optional;

/**
 * Services KMM provides to a plugin. Plugins deliberately see no Spring context, only this narrow interface.
 */
public interface PluginContext {

    /**
     * Setting of the plugin, e.g. {@code api-key} or {@code language}. Configured via
     * {@code kmm.plugins.settings.<plugin-id>.<name>}.
     */
    Optional<String> setting(String name);

    /** Shared HTTP client of the application (timeouts, later also a proxy). */
    HttpClient httpClient();

    /** User agent plugins identify themselves with at providers. */
    String userAgent();
}
