package de.tiktak.kmm.kodi.rpc;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Die Videoquellen eines Kodi-Geräts. Ein Scan außerhalb dieser Quellen endet in Kodi ohne Ergebnis und ohne Fehler.
 * Zugangsdaten in den Adressen der Quellen fallen weg, weil die Pfade in Meldungen und im Log erscheinen.
 */
public final class KodiSources {

    private static final String MULTIPATH_PREFIX = "multipath://";
    private static final String ADDONS_PREFIX = "addons://";
    private static final Pattern CREDENTIALS = Pattern.compile("^([a-zA-Z]+://)[^/@]+@");

    private final List<String> paths;

    private KodiSources(List<String> paths) {
        this.paths = paths;
    }

    /** @param files Quellen, wie Kodi sie liefert, auch zusammengesetzte ({@code multipath://}) und Add-ons */
    static KodiSources of(Collection<String> files) {
        return new KodiSources(files.stream()
                .flatMap(KodiSources::singlePaths)
                .filter(file -> !file.startsWith(ADDONS_PREFIX))
                .map(file -> CREDENTIALS.matcher(file).replaceFirst("$1"))
                .toList());
    }

    /** @return ob das Verzeichnis in oder unterhalb einer Quelle liegt */
    public boolean contain(String directory) {
        String wanted = comparable(directory);
        return paths.stream().map(KodiSources::comparable).anyMatch(wanted::startsWith);
    }

    /** @return ob der Pfad in einer Quelle liegt oder eine Quelle enthält; eine Pfad-Zuordnung darf breiter sein */
    public boolean overlap(String path) {
        String wanted = comparable(path);
        return contain(path) || paths.stream().map(KodiSources::comparable).anyMatch(source -> source.startsWith(wanted));
    }

    /** @return die Quellen für eine Meldung */
    @Override
    public String toString() {
        return paths.isEmpty() ? "keine" : String.join(", ", paths);
    }

    private static Stream<String> singlePaths(String file) {
        if (!file.startsWith(MULTIPATH_PREFIX)) {
            return Stream.of(file);
        }
        return Arrays.stream(file.substring(MULTIPATH_PREFIX.length()).split("/"))
                .filter(part -> !part.isEmpty())
                .map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8));
    }

    /** Kodi vergleicht Pfade nicht einheitlich; die Prüfung soll nur eindeutig falsche Pfade melden. */
    private static String comparable(String path) {
        String normalized = CREDENTIALS.matcher(path).replaceFirst("$1").replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
