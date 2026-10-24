package de.tiktak.kmm.plugin;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

/**
 * Ein Plugin mit der angefragten ID ist nicht installiert. Wird als HTTP 404 beantwortet.
 */
public class PluginNotFoundException extends ErrorResponseException {

    PluginNotFoundException(String pluginId) {
        super(HttpStatus.NOT_FOUND);
        getBody().setTitle("Plugin nicht gefunden");
        getBody().setDetail("Es ist kein Plugin mit der ID '%s' installiert.".formatted(pluginId));
    }
}
