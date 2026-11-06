package de.tiktak.kmm.migration.ember.kodi;

import java.util.List;

/**
 * A Kodi device from Ember's {@code Interface.Kodi.xml}.
 *
 * @param label        display name in Ember
 * @param address      host name or IP address
 * @param port         port of the Kodi web server; {@code 0} if Ember knows none
 * @param username     user name or {@code null}
 * @param password     password in plain text or {@code null}
 * @param realtimeSync whether Ember updated Kodi after every change
 * @param sources      mapping of Ember paths to Kodi paths
 */
public record EmberKodiHost(
        String label,
        String address,
        int port,
        String username,
        String password,
        boolean realtimeSync,
        List<Source> sources) {

    public EmberKodiHost {
        sources = List.copyOf(sources);
    }

    /**
     * @param contentType {@code Movie} or {@code TV}
     * @param localPath   path as Ember knew it, e.g. {@code a:\medien\filme}
     * @param remotePath  the same location as Kodi sees it, e.g. {@code smb://nas/medien/filme/}
     */
    public record Source(String contentType, String localPath, String remotePath) {
    }
}
