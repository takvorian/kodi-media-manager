package de.tiktak.kmm.kodi;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import de.tiktak.kmm.kodi.rpc.KodiException;
import de.tiktak.kmm.kodi.rpc.KodiSources;
import de.tiktak.kmm.kodi.rpc.KodiVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the Kodi devices. Public interface of the module for other modules.
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

    /** State of all switched-on devices for a sync. */
    List<KodiTarget> targets() {
        return repository.findAll(BY_NAME).stream().filter(KodiHost::isEnabled).map(KodiTarget::of).toList();
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

    /** Switches the device on or off; a switched-off device is left out of every sync. */
    @Transactional
    public KodiHostResponse switchTo(long hostId, boolean enabled) {
        KodiHost host = require(hostId);
        host.switchTo(enabled);
        log.info("Kodi host '{}' switched {}", host.getName(), enabled ? "on" : "off");
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
     * Imports a Kodi device from a migration. An existing device with the same name is updated.
     *
     * @return whether the device was newly created
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
     * Checks the connection to the device.
     *
     * @throws KodiProblemException if Kodi cannot be reached or rejects the login
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
     * Checks the path mapping against the sources of the device. If KMM cannot read the sources, there is no
     * warning: the connection test itself then reports whether Kodi can be reached.
     *
     * @return warning if Kodi paths of the mapping lie outside all sources
     */
    public Optional<LocalizedText> pathMappingWarning(long hostId) {
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
        return Optional.of(LocalizedText.of("kodi.host.noSourceWarning")
                .with("paths", String.join(", ", unknown))
                .with("sources", sources.describe()));
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
