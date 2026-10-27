package de.tiktak.kmm.nfo;

/**
 * Stream-Details einer Episode in einer (Mehrfachepisoden-)NFO.
 */
public record EpisodeStreamDetails(int season, int episode, StreamDetails details) {
}
