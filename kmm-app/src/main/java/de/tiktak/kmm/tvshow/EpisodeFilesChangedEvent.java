package de.tiktak.kmm.tvshow;

/**
 * NFO oder Standbild einer Episode wurden geschrieben oder gelöscht.
 */
public record EpisodeFilesChangedEvent(long episodeId) {
}
