package de.tiktak.kmm.kodi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import de.tiktak.kmm.kodi.rpc.KodiPlayback;

/**
 * Finds the playback state of a file among thousands from Kodi without searching all of them. Kodi compares paths
 * case-insensitively, and so does the index.
 */
final class KodiPlaybackIndex {

    private final Map<String, List<KodiPlayback>> byFile = new HashMap<>();

    KodiPlaybackIndex(List<KodiPlayback> playbacks) {
        playbacks.forEach(playback -> playback.files()
                .forEach(file -> byFile.computeIfAbsent(key(file), key -> new ArrayList<>()).add(playback)));
    }

    /**
     * @param kodiFile file as Kodi sees it
     * @param matches  check that makes the entry unique, e.g. season and episode
     */
    Optional<KodiPlayback> find(String kodiFile, Predicate<KodiPlayback> matches) {
        return byFile.getOrDefault(key(kodiFile), List.of()).stream().filter(matches).findFirst();
    }

    private static String key(String file) {
        return file.toLowerCase(Locale.ROOT);
    }
}
