package de.tiktak.kmm.nfo;

/**
 * Stream details of an episode in a (multi-episode) NFO.
 */
public record EpisodeStreamDetails(int season, int episode, StreamDetails details) {
}
