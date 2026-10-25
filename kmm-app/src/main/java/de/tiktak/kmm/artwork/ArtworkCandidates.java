package de.tiktak.kmm.artwork;

import java.util.List;

/**
 * Ergebnis einer Bildsuche.
 *
 * @param warnings Hinweise zu Scrapern, die nicht eingerichtet sind oder einen Fehler gemeldet haben
 */
public record ArtworkCandidates(List<ArtworkCandidate> candidates, List<String> warnings) {

    public ArtworkCandidates {
        candidates = List.copyOf(candidates);
        warnings = List.copyOf(warnings);
    }
}
