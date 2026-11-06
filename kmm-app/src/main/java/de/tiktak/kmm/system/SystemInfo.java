package de.tiktak.kmm.system;

import java.util.List;

/**
 * Basic information about the running instance.
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
