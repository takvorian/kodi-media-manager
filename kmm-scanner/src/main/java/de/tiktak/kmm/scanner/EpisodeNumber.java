package de.tiktak.kmm.scanner;

import java.util.List;
import java.util.Objects;

/**
 * Staffel und Episodennummern einer Episodendatei. Mehrfachepisoden ({@code S01E01E02}) enthalten
 * mehrere Nummern in aufsteigender Reihenfolge.
 *
 * @param season   Staffel; 0 für Specials
 * @param episodes Episodennummern, mindestens eine
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
