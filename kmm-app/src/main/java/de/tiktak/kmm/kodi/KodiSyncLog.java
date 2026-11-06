package de.tiktak.kmm.kodi;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Remembers the last sync result per Kodi device so that errors of the realtime sync become visible in the user
 * interface. Deliberately only in memory: after a restart the next sync counts.
 */
@Component
class KodiSyncLog {

    private final Map<Long, KodiSyncResult> lastResults = new ConcurrentHashMap<>();

    void record(KodiSyncResult result) {
        lastResults.put(result.hostId(), result);
    }

    Optional<KodiSyncResult> lastResult(long hostId) {
        return Optional.ofNullable(lastResults.get(hostId));
    }

    void forget(long hostId) {
        lastResults.remove(hostId);
    }
}
