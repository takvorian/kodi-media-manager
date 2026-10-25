package de.tiktak.kmm.kodi;

import java.util.List;
import java.util.Optional;

/**
 * Darstellung eines Kodi-Geräts für die REST-API, ohne Passwort.
 *
 * @param passwordSet ob ein Passwort gespeichert ist
 * @param lastSync    letzter Abgleich seit dem Start von KMM oder {@code null}
 */
public record KodiHostResponse(
        long id,
        String name,
        String address,
        int port,
        String username,
        boolean passwordSet,
        boolean realtimeSync,
        List<KodiHostCommand.PathMapping> pathMappings,
        KodiSyncResult lastSync) {

    static KodiHostResponse from(KodiHost host, Optional<KodiSyncResult> lastSync) {
        return new KodiHostResponse(host.getId(), host.getName(), host.getAddress(), host.getPort(),
                host.getUsername(), host.hasPassword(), host.isRealtimeSync(),
                host.pathMappings().stream().map(KodiHostCommand.PathMapping::of).toList(), lastSync.orElse(null));
    }
}
