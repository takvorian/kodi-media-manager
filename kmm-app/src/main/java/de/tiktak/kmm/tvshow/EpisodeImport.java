package de.tiktak.kmm.tvshow;

import java.time.Instant;
import java.util.Objects;

/**
 * Eine Episode aus einer Migration.
 *
 * @param filePath absoluter Pfad der Episodendatei
 * @param extras   weitere Angaben; ohne Spuren bleiben die bekannten Spuren erhalten
 * @param addedAt  Aufnahme in die Bibliothek oder {@code null} für „jetzt“
 */
public record EpisodeImport(String filePath, int season, int episode, EpisodeMetadata metadata, EpisodeExtras extras,
        Instant addedAt) {

    public EpisodeImport {
        Objects.requireNonNull(filePath, "filePath must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        extras = extras == null ? EpisodeExtras.EMPTY : extras;
    }
}
