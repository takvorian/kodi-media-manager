package de.tiktak.kmm.settings;

import java.util.List;
import java.util.Set;

/**
 * Wirksame Einstellungen für die Oberfläche. Das Proxy-Passwort wird nie ausgegeben.
 *
 * @param overridden Einstellungen, die durch die Konfiguration festgelegt sind und in der
 *                   Oberfläche nicht geändert werden können
 */
public record SettingsView(
        String metadataLanguage,
        String artworkLanguage,
        List<String> videoExtensions,
        List<String> excludedPaths,
        Proxy proxy,
        TvSettings tv,
        MediaInfoSettings mediaInfo,
        Set<SettingsField> overridden) {

    static SettingsView of(KmmSettings settings, Set<SettingsField> overridden) {
        ProxySettings proxy = settings.proxy();
        return new SettingsView(settings.metadataLanguage(), settings.artworkLanguage(), settings.videoExtensions(),
                settings.excludedPaths(),
                proxy == null ? null : new Proxy(proxy.host(), proxy.port(), proxy.username(), proxy.password() != null),
                settings.tv(), settings.mediaInfo(), Set.copyOf(overridden));
    }

    /**
     * @param passwordSet ob ein Passwort gespeichert ist
     */
    public record Proxy(String host, int port, String username, boolean passwordSet) {
    }
}
