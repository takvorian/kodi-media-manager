package de.tiktak.kmm.kodi;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Merkt sich das letzte Abgleichsergebnis je Kodi-Gerät, damit Fehler des Echtzeit-Syncs in der
 * Oberfläche sichtbar werden. Bewusst nur im Speicher: nach einem Neustart zählt der nächste Abgleich.
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
