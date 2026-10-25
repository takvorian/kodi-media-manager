package de.tiktak.kmm.kodi;

import de.tiktak.kmm.kodi.path.KodiPathMapper;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;

/**
 * Unveränderlicher Stand eines Kodi-Geräts für einen Abgleich außerhalb der Datenbanktransaktion.
 */
record KodiTarget(long id, String name, KodiEndpoint endpoint, KodiPathMapper pathMapper, boolean realtimeSync) {

    static KodiTarget of(KodiHost host) {
        return new KodiTarget(host.getId(), host.getName(), host.endpoint(), new KodiPathMapper(host.pathMappings()),
                host.isRealtimeSync());
    }
}
