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
 * Findet den Wiedergabestand einer Datei unter tausenden aus Kodi, ohne alle zu durchsuchen. Kodi
 * vergleicht Pfade ohne Rücksicht auf Groß- und Kleinschreibung, der Index ebenso.
 */
final class KodiPlaybackIndex {

    private final Map<String, List<KodiPlayback>> byFile = new HashMap<>();

    KodiPlaybackIndex(List<KodiPlayback> playbacks) {
        playbacks.forEach(playback -> playback.files()
                .forEach(file -> byFile.computeIfAbsent(key(file), key -> new ArrayList<>()).add(playback)));
    }

    /**
     * @param kodiFile Datei aus Sicht von Kodi
     * @param matches  Prüfung, die den Eintrag eindeutig macht, z. B. Staffel und Episode
     */
    Optional<KodiPlayback> find(String kodiFile, Predicate<KodiPlayback> matches) {
        return byFile.getOrDefault(key(kodiFile), List.of()).stream().filter(matches).findFirst();
    }

    private static String key(String file) {
        return file.toLowerCase(Locale.ROOT);
    }
}
