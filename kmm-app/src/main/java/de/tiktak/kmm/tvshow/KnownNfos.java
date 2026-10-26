package de.tiktak.kmm.tvshow;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Änderungszeiten der NFOs, die beim letzten Scan einer Quelle eingelesen wurden. Eine NFO mit
 * unveränderter Zeit muss der nächste Scan nicht erneut lesen.
 *
 * @param shows        Zeit der {@code tvshow.nfo} je Serienordner
 * @param episodeFiles Zeiten je Episodendatei; bei Mehrfachepisoden eine je Episode, {@code null} für
 *                     Episoden ohne eingelesene NFO
 */
record KnownNfos(Map<String, Instant> shows, Map<String, Set<Instant>> episodeFiles) {

    static final KnownNfos NONE = new KnownNfos(Map.of(), Map.of());

    KnownNfos {
        shows = Map.copyOf(shows);
        episodeFiles = Map.copyOf(episodeFiles);
    }

    boolean isCurrentShowNfo(String directory, TvNfoState nfo) {
        return nfo.modifiedAt().equals(shows.get(directory));
    }

    /** Aktuell ist die NFO nur, wenn alle Episoden der Datei sie mit dieser Zeit eingelesen haben. */
    boolean isCurrentEpisodeNfo(String file, TvNfoState nfo) {
        Set<Instant> known = episodeFiles.get(file);
        return known != null && known.size() == 1 && known.contains(nfo.modifiedAt());
    }
}
