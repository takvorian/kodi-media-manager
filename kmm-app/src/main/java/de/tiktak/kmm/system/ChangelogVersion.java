package de.tiktak.kmm.system;

import java.util.List;

/**
 * A released version with its changes.
 *
 * @param version name of the version, e.g. {@code v0.1}
 * @param date    date of the release, e.g. {@code 2026-10-30}; empty if missing
 * @param changes changes of this version
 */
public record ChangelogVersion(String version, String date, List<String> changes) {

    public ChangelogVersion {
        changes = List.copyOf(changes);
    }
}
