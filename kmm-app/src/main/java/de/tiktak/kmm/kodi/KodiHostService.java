package de.tiktak.kmm.kodi;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import de.tiktak.kmm.kodi.rpc.KodiException;
import de.tiktak.kmm.kodi.rpc.KodiSources;
import de.tiktak.kmm.kodi.rpc.KodiVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Verwaltet die Kodi-Geräte. Öffentliche Schnittstelle des Moduls für andere Module.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KodiHostService {

    private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name").ignoreCase());

    private final KodiHostRepository repository;
    private final KodiClient kodiClient;
    private final KodiSyncLog syncLog;

    public List<KodiHostResponse> findAll() {
        return repository.findAll(BY_NAME).stream().map(this::toResponse).toList();
    }

    /** Stand aller Geräte für einen Abgleich. */
    List<KodiTarget> targets() {
        return repository.findAll(BY_NAME).stream().map(KodiTarget::of).toList();
    }

    @Transactional
    public KodiHostResponse create(KodiHostSettings settings) {
        requireUniqueName(settings.name(), null);
        KodiHost host = repository.save(new KodiHost(settings));
        log.info("Kodi host '{}' created for {}:{}", host.getName(), host.getAddress(), host.getPort());
        return toResponse(host);
    }

    @Transactional
    public KodiHostResponse update(long hostId, KodiHostSettings settings) {
        KodiHost host = require(hostId);
        requireUniqueName(settings.name(), hostId);
        host.apply(settings);
        log.info("Kodi host '{}' updated", host.getName());
        return toResponse(host);
    }

    @Transactional
    public void delete(long hostId) {
        KodiHost host = require(hostId);
        repository.delete(host);
        syncLog.forget(hostId);
        log.info("Kodi host '{}' deleted", host.getName());
    }

    /**
     * Übernimmt ein Kodi-Gerät aus einer Migration. Ein vorhandenes Gerät mit demselben Namen wird
     * aktualisiert.
     *
     * @return ob das Gerät neu angelegt wurde
     */
    @Transactional
    public boolean importHost(KodiHostSettings settings) {
        var existing = repository.findByNameIgnoreCase(settings.name());
        if (existing.isPresent()) {
            existing.get().apply(settings);
            return false;
        }
        repository.save(new KodiHost(settings));
        log.info("Kodi host '{}' imported", settings.name());
        return true;
    }

    /**
     * Prüft die Verbindung zum Gerät.
     *
     * @throws KodiProblemException wenn Kodi nicht erreichbar ist oder die Anmeldung ablehnt
     */
    public KodiVersion testConnection(long hostId) {
        KodiEndpoint endpoint = require(hostId).endpoint();
        try {
            return kodiClient.version(endpoint);
        } catch (KodiException e) {
            throw KodiProblemException.unavailable(e);
        }
    }

    /**
     * Prüft die Pfad-Zuordnung gegen die Quellen des Geräts. Kann KMM die Quellen nicht lesen, gibt es keine Warnung:
     * Der Verbindungstest selbst meldet dann, ob Kodi erreichbar ist.
     *
     * @return Warnung, wenn Kodi-Pfade der Zuordnung außerhalb aller Quellen liegen
     */
    public Optional<String> pathMappingWarning(long hostId) {
        KodiHost host = require(hostId);
        KodiSources sources;
        try {
            sources = kodiClient.videoSources(host.endpoint());
        } catch (KodiException e) {
            log.debug("Could not read the sources of Kodi host '{}': {}", host.getName(), e.getMessage());
            return Optional.empty();
        }
        List<String> unknown = host.pathMappings().stream()
                .map(KodiPathMapping::kodiPath)
                .filter(path -> !sources.overlap(path))
                .toList();
        if (unknown.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Kodi hat keine Quelle für %s. Prüfe die Pfad-Zuordnung. Quellen in Kodi: %s."
                .formatted(String.join(", ", unknown), sources));
    }

    private KodiHostResponse toResponse(KodiHost host) {
        return KodiHostResponse.from(host, syncLog.lastResult(host.getId()));
    }

    private KodiHost require(long hostId) {
        return repository.findById(hostId).orElseThrow(() -> KodiProblemException.hostNotFound(hostId));
    }

    private void requireUniqueName(String name, Long ownId) {
        repository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(ownId))
                .ifPresent(other -> {
                    throw KodiProblemException.nameTaken(name);
                });
    }
}
