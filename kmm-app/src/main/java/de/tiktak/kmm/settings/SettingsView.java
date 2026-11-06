package de.tiktak.kmm.settings;

import java.util.List;
import java.util.Set;

/**
 * Effective settings for the user interface. The proxy password is never output.
 *
 * @param overridden settings that are fixed by the configuration and cannot be changed in the
 *                   user interface
 */
public record SettingsView(
        String metadataLanguage,
        String artworkLanguage,
        List<String> videoExtensions,
        List<String> excludedPaths,
        Proxy proxy,
        TvSettings tv,
        MediaInfoSettings mediaInfo,
        RenameSettings rename,
        MovieSetSettings movieSets,
        Set<SettingsField> overridden) {

    static SettingsView of(KmmSettings settings, Set<SettingsField> overridden) {
        ProxySettings proxy = settings.proxy();
        return new SettingsView(settings.metadataLanguage(), settings.artworkLanguage(), settings.videoExtensions(),
                settings.excludedPaths(),
                proxy == null ? null : new Proxy(proxy.host(), proxy.port(), proxy.username(), proxy.password() != null),
                settings.tv(), settings.mediaInfo(), settings.rename(), settings.movieSets(),
                Set.copyOf(overridden));
    }

    /**
     * @param passwordSet whether a password is stored
     */
    public record Proxy(String host, int port, String username, boolean passwordSet) {
    }
}
