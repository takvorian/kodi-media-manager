package de.tiktak.kmm.scanner;

import java.util.List;
import java.util.Objects;

/**
 * Season and episode numbers of an episode file. Multi-episode files ({@code S01E01E02}) contain several numbers in
 * ascending order.
 *
 * @param season   season; 0 for specials
 * @param episodes episode numbers, at least one
 */
public record EpisodeNumber(int season, List<Integer> episodes) {

    public EpisodeNumber {
        Objects.requireNonNull(episodes, "episodes must not be null");
        if (season < 0) {
            throw new IllegalArgumentException("season must not be negative");
        }
        if (episodes.isEmpty()) {
            throw new IllegalArgumentException("episodes must not be empty");
        }
        episodes = List.copyOf(episodes);
    }

    public int firstEpisode() {
        return episodes.getFirst();
    }
}
