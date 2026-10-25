package de.tiktak.kmm.kodi;

import java.util.List;

/**
 * Darstellung eines Kodi-Geräts für die REST-API, ohne Passwort.
 *
 * @param passwordSet ob ein Passwort gespeichert ist
 */
public record KodiHostResponse(
        long id,
        String name,
        String address,
        int port,
        String username,
        boolean passwordSet,
        boolean realtimeSync,
        List<KodiHostCommand.PathMapping> pathMappings) {

    static KodiHostResponse from(KodiHost host) {
        return new KodiHostResponse(host.getId(), host.getName(), host.getAddress(), host.getPort(),
                host.getUsername(), host.hasPassword(), host.isRealtimeSync(),
                host.pathMappings().stream().map(KodiHostCommand.PathMapping::of).toList());
    }
}
