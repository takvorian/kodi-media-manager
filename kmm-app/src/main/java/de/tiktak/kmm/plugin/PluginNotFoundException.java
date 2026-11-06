package de.tiktak.kmm.plugin;

import org.springframework.http.HttpStatus;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.web.LocalizedProblemException;

/**
 * No plugin with the requested ID is installed. Answered as HTTP 404.
 */
public class PluginNotFoundException extends LocalizedProblemException {

    PluginNotFoundException(String pluginId) {
        super(HttpStatus.NOT_FOUND, LocalizedText.of("plugin.notFound.title"),
                LocalizedText.of("plugin.notFound.detail").with("id", pluginId), null);
    }
}
