package de.tiktak.kmm.tvshow;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Modification times of the NFOs that were read during the last scan of a source. An NFO with
 * an unchanged time does not have to be read again by the next scan.
 *
 * @param shows        time of the {@code tvshow.nfo} per TV show folder
 * @param episodeFiles times per episode file; for multi-episodes one per episode, {@code null} for
 *                     episodes without an NFO that was read
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

    /** The NFO is up to date only if all episodes of the file have read it with this time. */
    boolean isCurrentEpisodeNfo(String file, TvNfoState nfo) {
        Set<Instant> known = episodeFiles.get(file);
        return known != null && known.size() == 1 && known.contains(nfo.modifiedAt());
    }
}
