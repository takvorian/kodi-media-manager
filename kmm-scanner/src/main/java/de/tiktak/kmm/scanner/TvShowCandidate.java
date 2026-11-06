package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A found TV show: a folder directly below the source with its episodes.
 *
 * @param directory           TV show folder
 * @param title               title according to the folder name
 * @param year                year according to the folder name or {@code null}
 * @param imdbId              IMDb ID according to the folder name or {@code null}
 * @param episodes            recognized episodes, sorted by season and episode
 * @param unrecognizedFiles   video files without a recognizable season and episode
 */
public record TvShowCandidate(Path directory, String title, Integer year, String imdbId,
        List<EpisodeCandidate> episodes, List<Path> unrecognizedFiles) {

    public TvShowCandidate {
        Objects.requireNonNull(directory, "directory must not be null");
        Objects.requireNonNull(title, "title must not be null");
        episodes = List.copyOf(episodes);
        unrecognizedFiles = List.copyOf(unrecognizedFiles);
    }
}
