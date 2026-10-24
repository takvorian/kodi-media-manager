package de.tiktak.kmm.system;

import java.util.List;

/**
 * Basisinformationen über die laufende Instanz.
 */
public record SystemInfo(
        String applicationName,
        String version,
        String javaVersion,
        List<String> activeProfiles,
        String database) {

    public SystemInfo {
        activeProfiles = List.copyOf(activeProfiles);
    }
}
