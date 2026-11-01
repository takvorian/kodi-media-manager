package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Eine gefundene Serie: ein Ordner direkt unterhalb der Quelle mit ihren Episoden.
 *
 * @param directory           Serienordner
 * @param title               Titel laut Ordnername
 * @param year                Jahr laut Ordnername oder {@code null}
 * @param imdbId              IMDb-ID laut Ordnername oder {@code null}
 * @param episodes            erkannte Episoden, nach Staffel und Episode sortiert
 * @param unrecognizedFiles   Videodateien ohne erkennbare Staffel und Episode
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
