package de.tiktak.kmm.source;

/**
 * Result of taking over a source from a migration.
 *
 * @param created whether the source was newly created ({@code false}: existing source with the same directory)
 */
public record ImportedSource(MediaSource source, boolean created) {
}
