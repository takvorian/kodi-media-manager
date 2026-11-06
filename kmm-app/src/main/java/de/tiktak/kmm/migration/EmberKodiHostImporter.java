package de.tiktak.kmm.migration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.kodi.KodiHostService;
import de.tiktak.kmm.kodi.KodiHostSettings;
import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHost;
import lombok.RequiredArgsConstructor;

/**
 * Imports Ember's Kodi devices with credentials. Ember's local paths become KMM paths via the path mapping of the
 * import; Kodi's paths stay unchanged. Runs in the caller's transaction.
 */
@Component
@RequiredArgsConstructor
class EmberKodiHostImporter {

    static final int DEFAULT_PORT = 8080;
    private static final int MAX_PORT = 65_535;

    private final KodiHostService hostService;

    void importHosts(List<EmberKodiHost> hosts, EmberPathTranslator paths, EmberImportProgress progress) {
        for (EmberKodiHost host : hosts) {
            if (host.address() == null) {
                progress.warn(LocalizedText.of("migration.warning.kodiHostWithoutAddress"));
                continue;
            }
            String name = name(host);
            boolean created = hostService.importHost(new KodiHostSettings(name, host.address(), port(host),
                    host.username(), host.password(), host.realtimeSync(), mappings(host, name, paths, progress)));
            progress.kodiHostImported(created);
        }
    }

    private static List<KodiPathMapping> mappings(EmberKodiHost host, String name, EmberPathTranslator paths,
            EmberImportProgress progress) {
        Map<String, KodiPathMapping> mappings = new LinkedHashMap<>();
        for (EmberKodiHost.Source source : host.sources()) {
            if (source.localPath() == null || source.remotePath() == null) {
                continue;
            }
            Optional<String> localPath = paths.translate(source.localPath());
            if (localPath.isEmpty()) {
                progress.warn(LocalizedText.of("migration.warning.kodiMappingMissing").with("host", name)
                        .with("local", source.localPath()).with("remote", source.remotePath()));
                continue;
            }
            mappings.putIfAbsent(localPath.get(), new KodiPathMapping(localPath.get(), source.remotePath()));
        }
        return List.copyOf(mappings.values());
    }

    private static String name(EmberKodiHost host) {
        String name = host.label() == null ? host.address() : host.label();
        return name.length() > KodiHostSettings.MAX_NAME_LENGTH ? name.substring(0, KodiHostSettings.MAX_NAME_LENGTH)
                : name;
    }

    private static int port(EmberKodiHost host) {
        return host.port() >= 1 && host.port() <= MAX_PORT ? host.port() : DEFAULT_PORT;
    }
}
