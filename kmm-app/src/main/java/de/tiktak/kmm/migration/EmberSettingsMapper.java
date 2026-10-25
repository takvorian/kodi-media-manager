package de.tiktak.kmm.migration;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.ProxySettings;

/**
 * Überträgt Ember-Einstellungen auf die KMM-Einstellungen. Übernommen wird nur, was in Ember gültig
 * gesetzt ist; alles andere bleibt, wie es in KMM ist.
 */
final class EmberSettingsMapper {

    private static final Pattern METADATA_LANGUAGE = Pattern.compile("[a-z]{2}(-[A-Z]{2})?");
    private static final Pattern ARTWORK_LANGUAGE = Pattern.compile("[a-z]{2}");
    private static final Pattern EXTENSION = Pattern.compile("\\.?[a-z0-9]{1,10}");
    private static final int DEFAULT_PROXY_PORT = 8080;

    private EmberSettingsMapper() {
    }

    /**
     * @param excludedPaths aus Ember übernommene, bereits zugeordnete ausgeschlossene Ordner
     */
    static SettingsImport map(KmmSettings current, Optional<EmberSettings> ember, List<String> excludedPaths) {
        List<String> changes = new ArrayList<>();
        String metadataLanguage = current.metadataLanguage();
        String artworkLanguage = current.artworkLanguage();
        List<String> videoExtensions = current.videoExtensions();
        ProxySettings proxy = current.proxy();
        if (ember.isPresent()) {
            EmberSettings settings = ember.get();
            if (matches(METADATA_LANGUAGE, settings.movieLanguage())) {
                metadataLanguage = settings.movieLanguage();
                changes.add("Sprache der Metadaten: " + metadataLanguage);
            }
            if (matches(ARTWORK_LANGUAGE, settings.movieImageLanguage())) {
                artworkLanguage = settings.movieImageLanguage();
                changes.add("Bevorzugte Bildsprache: " + artworkLanguage);
            }
            List<String> extensions = extensions(settings.videoExtensions());
            if (!extensions.isEmpty()) {
                videoExtensions = extensions;
                changes.add("Video-Dateiendungen: " + String.join(", ", extensions));
            }
            Optional<ProxySettings> emberProxy = settings.proxy().flatMap(EmberSettingsMapper::proxy);
            if (emberProxy.isPresent()) {
                proxy = emberProxy.get();
                changes.add("Proxy: %s:%d%s".formatted(proxy.host(), proxy.port(),
                        proxy.username() == null ? "" : " (Passwort bitte in den Einstellungen eingeben)"));
            }
        }
        Set<String> allExcluded = new LinkedHashSet<>(current.excludedPaths());
        if (allExcluded.addAll(excludedPaths)) {
            changes.add("Ausgeschlossene Ordner: " + String.join(", ", excludedPaths));
        }
        KmmSettings imported = new KmmSettings(metadataLanguage, artworkLanguage, videoExtensions,
                List.copyOf(allExcluded), proxy);
        return new SettingsImport(imported, changes);
    }

    private static List<String> extensions(List<String> emberExtensions) {
        return emberExtensions.stream()
                .map(extension -> extension.strip().toLowerCase(Locale.ROOT))
                .filter(EXTENSION.asMatchPredicate())
                .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                .distinct()
                .toList();
    }

    /** Ember speichert den Proxy als URI ({@code http://proxy.local}) und Port; das Passwort nicht portabel. */
    private static Optional<ProxySettings> proxy(EmberProxySettings ember) {
        String host = host(ember.uri().strip());
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        int port = ember.port() > 0 ? ember.port() : DEFAULT_PROXY_PORT;
        return Optional.of(new ProxySettings(host, port, ember.username(), null));
    }

    private static String host(String uri) {
        if (!uri.contains("://")) {
            return uri;
        }
        try {
            return URI.create(uri).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    /**
     * @param changes übernommene Werte in lesbarer Form
     */
    record SettingsImport(KmmSettings settings, List<String> changes) {
    }
}
