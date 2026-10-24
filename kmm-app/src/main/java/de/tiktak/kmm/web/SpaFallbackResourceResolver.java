package de.tiktak.kmm.web;

import java.io.IOException;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Liefert für Routen der Angular-App (z. B. {@code /movies/42}) die {@code index.html} aus, damit
 * Deep Links und Browser-Reloads funktionieren. Fehlende Dateien mit Endung und Backend-Pfade
 * bleiben 404.
 */
class SpaFallbackResourceResolver extends PathResourceResolver {

    static final String INDEX_FILE = "index.html";
    private static final List<String> BACKEND_PREFIXES = List.of("api/", "actuator/", "v3/", "swagger-ui/");
    private static final char EXTENSION_SEPARATOR = '.';
    private static final char PATH_SEPARATOR = '/';

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        Resource requested = super.getResource(resourcePath, location);
        if (requested != null) {
            return requested;
        }
        return isClientRoute(resourcePath) ? super.getResource(INDEX_FILE, location) : null;
    }

    private static boolean isClientRoute(String resourcePath) {
        return BACKEND_PREFIXES.stream().noneMatch(resourcePath::startsWith) && !hasFileExtension(resourcePath);
    }

    private static boolean hasFileExtension(String resourcePath) {
        String fileName = resourcePath.substring(resourcePath.lastIndexOf(PATH_SEPARATOR) + 1);
        return fileName.indexOf(EXTENSION_SEPARATOR) >= 0;
    }
}
