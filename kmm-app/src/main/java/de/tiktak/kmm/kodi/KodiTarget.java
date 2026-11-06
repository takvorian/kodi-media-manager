package de.tiktak.kmm.kodi;

import de.tiktak.kmm.kodi.path.KodiPathMapper;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;

/**
 * Immutable state of a Kodi device for a sync outside the database transaction.
 */
record KodiTarget(long id, String name, KodiEndpoint endpoint, KodiPathMapper pathMapper, boolean realtimeSync) {

    static KodiTarget of(KodiHost host) {
        return new KodiTarget(host.getId(), host.getName(), host.endpoint(), new KodiPathMapper(host.pathMappings()),
                host.isRealtimeSync());
    }
}
