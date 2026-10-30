package de.tiktak.kmm.system;

import java.util.List;

/**
 * Eine veröffentlichte Version mit ihren Änderungen.
 *
 * @param version Name der Version, z. B. {@code v0.1}
 * @param date    Datum des Release, z. B. {@code 2026-10-30}; leer, wenn es fehlt
 * @param changes Änderungen dieser Version
 */
public record ChangelogVersion(String version, String date, List<String> changes) {

    public ChangelogVersion {
        changes = List.copyOf(changes);
    }
}
