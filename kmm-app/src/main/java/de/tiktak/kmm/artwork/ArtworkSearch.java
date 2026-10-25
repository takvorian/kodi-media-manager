package de.tiktak.kmm.artwork;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.tiktak.kmm.plugin.api.ImageType;

/**
 * Treffer einer Bildsuche über alle Bildarten.
 *
 * @param candidatesByType je Bildart die Treffer in der Reihenfolge der Auswahl
 * @param warnings         Hinweise zu Scrapern, die nicht eingerichtet sind oder einen Fehler gemeldet haben
 */
record ArtworkSearch(Map<ImageType, List<ArtworkCandidate>> candidatesByType, List<String> warnings) {

    ArtworkSearch {
        Map<ImageType, List<ArtworkCandidate>> copy = new EnumMap<>(ImageType.class);
        candidatesByType.forEach((type, candidates) -> copy.put(type, List.copyOf(candidates)));
        candidatesByType = Collections.unmodifiableMap(copy);
        warnings = List.copyOf(warnings);
    }

    List<ArtworkCandidate> candidatesOf(ImageType type) {
        return candidatesByType.getOrDefault(type, List.of());
    }
}
