package de.tiktak.kmm.kodi;

import java.util.List;
import java.util.Optional;

/**
 * Representation of a Kodi device for the REST API, without password.
 *
 * @param passwordSet whether a password is stored
 * @param enabled     whether KMM contacts the device for syncs
 * @param lastSync    last sync since KMM started, or {@code null}
 */
public record KodiHostResponse(
        long id,
        String name,
        String address,
        int port,
        String username,
        boolean passwordSet,
        boolean realtimeSync,
        boolean enabled,
        List<KodiHostCommand.PathMapping> pathMappings,
        KodiSyncResult lastSync) {

    static KodiHostResponse from(KodiHost host, Optional<KodiSyncResult> lastSync) {
        return new KodiHostResponse(host.getId(), host.getName(), host.getAddress(), host.getPort(),
                host.getUsername(), host.hasPassword(), host.isRealtimeSync(), host.isEnabled(),
                host.pathMappings().stream().map(KodiHostCommand.PathMapping::of).toList(), lastSync.orElse(null));
    }
}
