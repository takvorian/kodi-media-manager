package de.tiktak.kmm.scanner;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Eine gefundene Episodendatei.
 *
 * @param file   Videodatei
 * @param number Staffel und Episode(n) laut Dateiname
 * @param title  Titelvorschlag aus dem Dateinamen oder {@code null}
 */
public record EpisodeCandidate(Path file, EpisodeNumber number, String title) {

    public EpisodeCandidate {
        Objects.requireNonNull(file, "file must not be null");
        Objects.requireNonNull(number, "number must not be null");
    }
}
